/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
 *  Steve Suh    <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */
package org.scandroid.flow.functions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.FieldElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.InstanceKeyElement;
import org.scandroid.domain.LocalElement;
import org.scandroid.domain.ReturnElement;
import org.scandroid.domain.StaticFieldElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.IReversibleFlowFunction;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.ConcreteTypeKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.intset.SparseIntSet;

public class TaintTransferFunctions<E extends ISSABasicBlock> implements
		IFlowFunctionMap<BasicBlockInContext<E>> {
	private static final Logger logger = LoggerFactory
			.getLogger(TaintTransferFunctions.class);

	private final IFDSTaintDomain<E> domain;
	private final ISupergraph<BasicBlockInContext<E>, CGNode> graph;
	private final PointerAnalysis pa;
	private final IUnaryFlowFunction globalId;
	private final IUnaryFlowFunction callToReturn;
	private final IUnaryFlowFunction callNoneToReturn;
	private final Map<Integer, IUnaryFlowFunction> callFlowFunctions;
	private final Map<Integer, IUnaryFlowFunction> normalFlowFunctions;

	public static final IntSet EMPTY_SET = new SparseIntSet();
	public static final IntSet ZERO_SET = SparseIntSet.singleton(0);

	private static final IReversibleFlowFunction IDENTITY_FN = new IdentityFlowFunction();

	public TaintTransferFunctions(IFDSTaintDomain<E> domain,
			ISupergraph<BasicBlockInContext<E>, CGNode> graph,
			PointerAnalysis pa) {
		this.domain = domain;
		this.graph = graph;
		this.pa = pa;
		this.globalId = new GlobalIdentityFunction<E>(domain);
		this.callToReturn = new CallToReturnFunction<E>(domain);
		this.callNoneToReturn = union(globalId,
				new CallNoneToReturnFunction<E>(domain));
		this.callFlowFunctions = Maps.newHashMap();
		this.normalFlowFunctions = Maps.newHashMap();
	}

	private static <E extends ISSABasicBlock> Integer fastHash2(
			BasicBlockInContext<E> block1, BasicBlockInContext<E> block2) {
		final int x = null == block1 ? 0 : block1.hashCode();
		final int y = null == block2 ? 0 : block2.hashCode();
		final int prime = 31;
		int result = 1;
		result = prime * result + x;
		result = prime * result + y;
		return Integer.valueOf(result);

	}

	@Override
	public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest, BasicBlockInContext<E> ret) {
		final Integer key = fastHash2(src, dest);
		IUnaryFlowFunction f = callFlowFunctions.get(key);
		if (null == f) {
			f = makeCallFlowFunction(src, dest, ret);
			callFlowFunctions.put(key, f);
		}
		return f;
	}

	private IUnaryFlowFunction makeCallFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest, BasicBlockInContext<E> ret) {
		logger.trace("getCallFlowFunction");
		SSAInstruction srcInst = src.getLastInstruction();
		if (null == srcInst) {
			logger.warn("null source for a call");
			return IDENTITY_FN;
		}

		if (srcInst instanceof SSAInvokeInstruction) {
			// build list of actual parameter code elements, and return a
			// function
			final int numParams = ((SSAInvokeInstruction) srcInst)
					.getNumberOfParameters();
			List<CodeElement> actualParams = Lists
					.newArrayListWithCapacity(numParams);
			for (int i = 0; i < numParams; i++) {
				actualParams.add(i, new LocalElement(srcInst.getUse(i)));
			}
			logger.trace("actual param list length: {}", actualParams);
			// return new TracingFlowFunction<E>(domain, union(new
			// GlobalIdentityFunction<E>(domain),
			// new CallFlowFunction<E>(domain, actualParams)));
			return union(globalId,
					new CallFlowFunction<E>(domain, actualParams));
		} else {
			throw new RuntimeException("src block not an invoke instruction");
		}
	}

	@Override
	public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		if (logger.isTraceEnabled()) {
			logger.trace("getNoneToReturnFunction");
			logger.trace("callee signature: {}", ((SSAInvokeInstruction) src
					.getLastInstruction()).getDeclaredTarget().getSignature());
		}
		// return callNoneToReturn;
		/*
		 * TODO: is this right?
		 * 
		 * The original callNoneToReturn impl just adds taints to absolutely
		 * everything in the domain. This seems like the wrong approach, but
		 * it's unclear what would be correct...
		 * 
		 * Switching this to the identity for now improves performance
		 * drastically.
		 */
		return IDENTITY_FN;
	}

	@Override
	public IUnaryFlowFunction getCallToReturnFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		if (logger.isTraceEnabled()) {
			logger.trace("getCallToReturnFunction\n\t{}\n\t-> {}", src
					.getMethod().getSignature(), dest.getMethod()
					.getSignature());
		}
		// return new TracingFlowFunction<E>(domain, new
		// CallToReturnFunction<E>(domain));
		return callToReturn;
	}

	@Override
	public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		final Integer key = fastHash2(src, dest);
		IUnaryFlowFunction f = normalFlowFunctions.get(key);
		if (null == f) {
			f = makeNormalFlowFunction(src, dest);
			normalFlowFunctions.put(key, f);
		}
		return f;
	}

	private IUnaryFlowFunction makeNormalFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		List<UseDefPair> pairs = Lists.newArrayList();

		if (logger.isTraceEnabled()) {
			logger.trace("getNormalFlowFunction {}", dest.getMethod()
					.getSignature());
		}

		// we first try to process the destination instruction
		SSAInstruction inst = dest.getLastInstruction();
		CGNode node = dest.getNode();

		// if (null == inst) {
		// final SSAInstruction srcInst = src.getLastInstruction();
		// if (null == srcInst) {
		// logger.debug("Using identity fn. for normal flow (src and dest instructions null)");
		// return IDENTITY_FN;
		// }
		// // if it's null, though, we'll process the src instruction.
		// // this *should* ensure we don't process the same instruction
		// // mulitple times
		// inst = srcInst;
		// node = src.getNode();
		// }

		if (null == inst) {
			// final SSAInstruction srcInst = src.getLastInstruction();
			// if (null == srcInst) {
			logger.trace("Using identity fn. for normal flow (dest instruction null)");
			return IDENTITY_FN;
			// }
			// // if it's null, though, we'll process the src instruction.
			// // this *should* ensure we don't process the same instruction
			// // mulitple times
			// inst = srcInst;
			// node = src.getNode();
		}

		logger.trace("\tinstruction: {}", inst);

		Iterable<CodeElement> inCodeElts = getInCodeElts(node, inst);
		Iterable<CodeElement> outCodeElts = getOutCodeElts(node, inst);
		if (!inCodeElts.iterator().hasNext()) {
			logger.trace("no input elements for {}", inst);
		}
		if (!outCodeElts.iterator().hasNext()) {
			logger.trace("no output elements for {}", inst);
		}

		// for now, take the Cartesian product of the inputs and outputs:
		// TODO specialize this on a per-instruction basis to improve precision.
		for (CodeElement use : inCodeElts) {
			for (CodeElement def : outCodeElts) {
				pairs.add(new UseDefPair(use, def));
			}
		}

		// globals may be redefined here, so we can't union with the globals ID
		// flow function, as we often do elsewhere.
		return new PairBasedFlowFunction<E>(domain, pairs);
	}

	/*
	 * The usual arguments:
	 * 
	 * call: the invoke instruction that took us into this method
	 * 
	 * src: a block that's the postdominator of this method, usually with no
	 * instructions
	 * 
	 * dest: whatever instruction followed the invoke instruction in call
	 * 
	 * What we want to accomplish:
	 * 
	 * 1. Map taints from the value being returned to a LocalElement in the
	 * caller's context
	 * 
	 * 2. Pass through any global information that the callee may have changed
	 * 
	 * 3. Process ins/outs of dest block as well (it will never be the dest of a
	 * NormalFlowFunction)
	 * 
	 * @see
	 * com.ibm.wala.dataflow.IFDS.IFlowFunctionMap#getReturnFlowFunction(java
	 * .lang.Object, java.lang.Object, java.lang.Object)
	 */
	@Override
	public IFlowFunction getReturnFlowFunction(BasicBlockInContext<E> call,
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		if (logger.isTraceEnabled()) {
			logger.trace("getReturnFlowFunction\n\t{}\n\t-> {}\n\t-> {}", call
					.getNode().getMethod().getSignature(), src.getNode()
					.getMethod().getSignature(), dest.getNode().getMethod()
					.getSignature());
			logger.trace("\t{} -> {} -> {}", call.getLastInstruction(),
					src.getLastInstruction(), dest.getLastInstruction());
		}
		final SSAInstruction inst = call.getLastInstruction();
		if (null == inst || !(inst instanceof SSAInvokeInstruction)) {
			// if we don't have an invoke, just punt and hope the necessary
			// information is already in global elements
			logger.warn("call block null or not an invoke instruction");
			return globalId;
		}

		// we always need to process the destination instruction
		final IUnaryFlowFunction flowFromDest = getNormalFlowFunction(null,
				dest);

		final SSAInvokeInstruction invoke = (SSAInvokeInstruction) inst;

		if (invoke.getNumberOfReturnValues() == 0) {
			// no return values, just propagate global information
			// return new TracingFlowFunction<E>(domain, compose (flowFromDest,
			// new GlobalIdentityFunction<E>(domain)));
			return compose(flowFromDest, globalId);
		}

		// we have a return value, so we need to map any return elements onto
		// the local element corresponding to the invoke's def
		final IUnaryFlowFunction flowToDest = union(globalId,
				new ReturnFlowFunction<E>(domain, invoke.getDef()));

		// return new TracingFlowFunction<E>(domain, compose(flowFromDest,
		// flowToDest));
		return compose(flowFromDest, flowToDest);
	}

	private Iterable<CodeElement> getOutCodeElts(CGNode node,
			SSAInstruction inst) {
		int defNo = inst.getNumberOfDefs();
		Set<CodeElement> elts = Sets.newHashSet();

		if (inst instanceof SSAReturnInstruction) {
			// only one possible element for returns
			if (logger.isTraceEnabled()) {
				logger.trace("making a return element for {}", node.getMethod()
						.getSignature());
			}
			elts.add(new ReturnElement());
			return elts;
		}

		if (inst instanceof SSAPutInstruction) {
			final Set<CodeElement> fieldAccessCodeElts = getFieldAccessCodeElts(
					node, (SSAPutInstruction) inst);
			if (logger.isTraceEnabled()) {
				logger.trace("put outelts: {}",
						Arrays.toString(fieldAccessCodeElts.toArray()));
			}
			elts.addAll(fieldAccessCodeElts);
		}

		if (inst instanceof SSAArrayStoreInstruction) {
			elts.addAll(getArrayRefCodeElts(node,
					(SSAArrayStoreInstruction) inst));
		}

		for (int i = 0; i < defNo; i++) {
			int valNo = inst.getDef(i);

			elts.addAll(CodeElement.valueElements(pa, node, valNo));
		}

		return elts;
	}

	private Iterable<CodeElement> getInCodeElts(CGNode node, SSAInstruction inst) {
		int useNo = inst.getNumberOfUses();
		Set<CodeElement> elts = Sets.newHashSet();

		if (inst instanceof SSAGetInstruction) {
			elts.addAll(getFieldAccessCodeElts(node, (SSAGetInstruction) inst));
		}

		if (inst instanceof SSAArrayLoadInstruction) {
			elts.addAll(getArrayRefCodeElts(node,
					(SSAArrayLoadInstruction) inst));
		}

		for (int i = 0; i < useNo; i++) {
			int valNo = inst.getUse(i);

			// Constants have valuenumber 0, which is otherwise, illegal.
			// these need to be skipped:
			if (0 == valNo) {
				continue;
			}
			try {
				elts.addAll(CodeElement.valueElements(pa, node, valNo));
			} catch (IllegalArgumentException e) {
				logger.error("Exception working on node: " + node);
				logger.error("Node is in method: " + node.getMethod());
				throw e;
			}
		}

		return elts;
	}

	// private Iterable<CodeElement> getOutCodeElts(final CGNode node, final
	// SSAInstruction inst) {
	// return new Iterable<CodeElement>() {
	// @Override
	// public Iterator<CodeElement> iterator() {
	// return new DefEltIterator(node, inst);
	// }
	// };
	// }
	//
	// private Iterable<CodeElement> getInCodeElts(final CGNode node, final
	// SSAInstruction inst) {
	// return new Iterable<CodeElement>() {
	// @Override
	// public Iterator<CodeElement> iterator() {
	// return new UseEltIterator(node, inst);
	// }
	// };
	// }

	private Set<CodeElement> getFieldAccessCodeElts(CGNode node,
			SSAFieldAccessInstruction inst) {
		if (inst.isStatic()) {
			return getStaticFieldAccessCodeElts(node, inst);
		}

		Set<CodeElement> elts = Sets.newHashSet();
		final FieldReference fieldRef = inst.getDeclaredField();
		final IField field = node.getClassHierarchy().resolveField(fieldRef);
		PointerKey pk = pa.getHeapModel().getPointerKeyForLocal(node,
				inst.getRef());

		final OrdinalSet<InstanceKey> pointsToSet = pa.getPointsToSet(pk);
		if (pointsToSet.isEmpty()) {
			logger.debug(
					"pointsToSet empty for ref of {}, creating InstanceKey manually",
					inst);
			InstanceKey ik = new ConcreteTypeKey(field.getDeclaringClass());
			elts.add(new FieldElement(ik, fieldRef));
			elts.add(new InstanceKeyElement(ik));
		} else {
			for (InstanceKey ik : pointsToSet) {
				if (logger.isTraceEnabled()) {
					logger.trace("adding elements for field {} on {}",
							field.getName(), ik.getConcreteType().getName());
				}
				elts.add(new FieldElement(ik, fieldRef));
				elts.add(new InstanceKeyElement(ik));
			}
		}
		return elts;
	}

	private Set<CodeElement> getStaticFieldAccessCodeElts(CGNode node,
			SSAFieldAccessInstruction inst) {
		Set<CodeElement> elts = Sets.newHashSet();
		final FieldReference fieldRef = inst.getDeclaredField();
		elts.add(new StaticFieldElement(fieldRef));
		// TODO: what about tainting the declaring class?

		return elts;
	}

	private Set<CodeElement> getArrayRefCodeElts(CGNode node,
			SSAArrayReferenceInstruction inst) {
		Set<CodeElement> elts = Sets.newHashSet();
		final PointerKey pk = pa.getHeapModel().getPointerKeyForLocal(node,
				inst.getArrayRef());
		final OrdinalSet<InstanceKey> pointsToSet = pa.getPointsToSet(pk);
		if (pointsToSet.isEmpty()) {
			logger.debug(
					"pointsToSet empty for ref of {}, creating InstanceKey manually",
					inst);
			TypeReference arrayType = TypeReference.findOrCreateArrayOf(inst
					.getElementType());
			InstanceKey ik = new ConcreteTypeKey(pa.getClassHierarchy()
					.lookupClass(arrayType));
			elts.add(new InstanceKeyElement(ik));
		} else {
			for (InstanceKey ik : pointsToSet) {
				if (logger.isTraceEnabled()) {
					logger.trace("adding element for array store in {}", ik
							.getConcreteType().getName());
				}
				elts.add(new InstanceKeyElement(ik));
			}
		}
		return elts;
	}

	private IUnaryFlowFunction union(final IUnaryFlowFunction g,
			final IUnaryFlowFunction h) {
		return new IUnaryFlowFunction() {
			@Override
			public IntSet getTargets(int d1) {
				return g.getTargets(d1).union(h.getTargets(d1));
			}
		};
	}

	/**
	 * Flow function composition
	 * 
	 * @param f
	 * @param g
	 * @return { (x, z) | (x, y) \in g, (y, z) \in f }
	 */
	private IUnaryFlowFunction compose(final IUnaryFlowFunction f,
			final IUnaryFlowFunction g) {
		return new IUnaryFlowFunction() {

			@Override
			public IntSet getTargets(int d1) {
				final MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
				g.getTargets(d1).foreach(new IntSetAction() {

					@Override
					public void act(int x) {
						set.addAll(f.getTargets(x));
					}
				});
				return set;
			}
		};
	}

	/*
	 * private class UseEltIterator implements Iterator<CodeElement> { private
	 * int idx = 0; private Iterator<CodeElement> subIt; private final CGNode
	 * node; private final SSAInstruction inst; private final int count;
	 * 
	 * public UseEltIterator(CGNode node, SSAInstruction inst) { this.node =
	 * node; this.inst = inst; count = inst.getNumberOfUses();
	 * updateIterator(node, inst); }
	 * 
	 * private void updateIterator(final CGNode node, final SSAInstruction inst)
	 * { int valNo = inst.getUse(idx); idx++; Set<CodeElement> elements =
	 * CodeElement.valueElements(pa, node, valNo); subIt = elements.iterator();
	 * }
	 * 
	 * @Override public boolean hasNext() { if (subIt.hasNext()) { return true;
	 * } else if (idx < count) { updateIterator(node, inst); return hasNext(); }
	 * else { return false; } }
	 * 
	 * @Override public CodeElement next() { return subIt.next(); }
	 * 
	 * @Override public void remove() {} }
	 * 
	 * private class DefEltIterator implements Iterator<CodeElement> { private
	 * int idx = 0; private Iterator<CodeElement> subIt; private final CGNode
	 * node; private final SSAInstruction inst; private final int count;
	 * 
	 * public DefEltIterator(CGNode node, SSAInstruction inst) { this.node =
	 * node; this.inst = inst; count = inst.getNumberOfDefs();
	 * updateIterator(node, inst); }
	 * 
	 * private void updateIterator(final CGNode node, final SSAInstruction inst)
	 * { int valNo = inst.getDef(idx); idx++; Set<CodeElement> elements =
	 * CodeElement.valueElements(pa, node, valNo); subIt = elements.iterator();
	 * }
	 * 
	 * @Override public boolean hasNext() { if (subIt.hasNext()) { return true;
	 * } else if (idx < count) { updateIterator(node, inst); return hasNext(); }
	 * else { return false; } }
	 * 
	 * @Override public CodeElement next() { return subIt.next(); }
	 * 
	 * @Override public void remove() {} }
	 */
}
