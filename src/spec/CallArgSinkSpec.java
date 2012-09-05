/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>)
 *  Steve Suh           <suhsteve@gmail.com>
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

package spec;

import java.util.Collection;
import java.util.HashSet;


import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import flow.types.ActivityCallFlow;
import flow.types.FlowType;
import flow.types.IKFlow;
import flow.types.ReturnFlow;
import flow.types.ServiceCallFlow;

public class CallArgSinkSpec extends SinkSpec {

	final String name = "CallArgSink";
	
    CallArgSinkSpec(MethodNamePattern name, int[] args) {
        namePattern = name;
        argNums = args;
        myType = SinkType.CALL_SINK; 
    }
    
    CallArgSinkSpec(MethodNamePattern name, int[] args, SinkType type) {
        namePattern = name;
        argNums = args;
        myType = type;
    }
    
	@Override
    public <E extends ISSABasicBlock> Collection<FlowType> getFlowType(
    		IMethod target, SSAInvokeInstruction invInst, CGNode node,
            int argNum, PointerAnalysis pa) {
    	
    	HashSet<FlowType> flowSet = new HashSet<FlowType>();
    	flowSet.clear();
    	
    	switch(myType) {
		case CALL_SINK:
		case ACTIVITY_SINK:
			flowSet.add(new ActivityCallFlow(invInst.getDeclaredTarget().getDeclaringClass(), node, name, target.getSignature(), argNum));
			break;
		case SERVICE_SINK:
            //flowSet.add(new CallArgSinkFlow(invInst,argNum, node));
            flowSet.add(new ServiceCallFlow(invInst.getDeclaredTarget().getDeclaringClass(), node, name, target.getSignature(), argNum));
            break;
		case RETURN_SINK:
			flowSet.add(new ReturnFlow(invInst.getDeclaredTarget().getDeclaringClass(), node, name, target.getSignature(), argNum));
			break;
		case PROVIDER_SINK:
            for(InstanceKey ik:pa.getPointsToSet(new LocalPointerKey(node, invInst.getUse(1))))
                //flowSet.add(new CallArgProviderSinkFlow(ik, invInst, argNum, node));
            	flowSet.add(new IKFlow(ik, pa.getInstanceKeyMapping().getMappedIndex(ik), node, name, target.getSignature(), argNum));
			break;
		default:
    		throw new UnsupportedOperationException("SourceType not yet Implemented");
    	}
    	
    	return flowSet;
    	
    }


    
}
