/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Jonathan Bardin     <astrosus@gmail.com>
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

package com.ibm.wala.classLoader;


import static org.jf.dexlib.Util.AccessFlags.FINAL;
import static org.jf.dexlib.Util.AccessFlags.PRIVATE;
import static org.jf.dexlib.Util.AccessFlags.PROTECTED;
import static org.jf.dexlib.Util.AccessFlags.PUBLIC;
import static org.jf.dexlib.Util.AccessFlags.STATIC;
import static org.jf.dexlib.Util.AccessFlags.VOLATILE;

import java.util.Collection;
import java.util.Collections;

import org.jf.dexlib.ClassDataItem.EncodedField;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;

public class DexIField implements IField {

/*
 * The EncodedFied object for which this DexIField is a wrapper
 */
    private final EncodedField eField;

    /**
     * The declaring class for this method.
     */
    private final DexIClass myClass;

    /**
     * name of the return type for this method,
     * construct in the get return type method.
     */
    //private TypeReference typeReference;

    /**
     * canonical FieldReference corresponding to this method,
     * construct in the getReference method.
     */
    private FieldReference fieldReference, myFieldRef;

    private Atom name;


    public DexIField(EncodedField encodedField, DexIClass klass) {
    //public DexIField(EncodedField encodedField) {
        eField = encodedField;
        myClass = klass;
        name = Atom.findOrCreateUnicodeAtom(eField.field.getFieldName().getStringValue());

        ImmutableByteArray fieldType = ImmutableByteArray.make(eField.field.getFieldType().getTypeDescriptor());
        TypeName T = null;
        if (fieldType.get(fieldType.length() - 1) == ';') {
            T = TypeName.findOrCreate(fieldType, 0, fieldType.length() - 1);
        } else {
            T = TypeName.findOrCreate(fieldType);
        }
        TypeReference type = TypeReference.findOrCreate(myClass.getClassLoader().getReference(), T);
        myFieldRef = FieldReference.findOrCreate(myClass.getReference(), name, type);
    }

    public TypeReference getFieldTypeReference() {

        //compute the typeReference from the EncodedField
//      if (typeReference == null) {
//          typeReference = TypeReference.findOrCreate(myClass.getClassLoader()
//                  .getReference(), eField.field.getFieldType().getTypeDescriptor());
//      }
//      return typeReference;
        return myFieldRef.getFieldType();

    }

    public FieldReference getReference() {

        if (fieldReference == null) {
//          fieldReference = FieldReference.findOrCreate(myClass.getReference(),
//                  eField.field.getContainingClass().getTypeDescriptor(), eField.field.getFieldName().getStringValue(),
//                  eField.field.getFieldType().getTypeDescriptor());
            fieldReference = FieldReference.findOrCreate(myClass.getReference(), getName(), getFieldTypeReference());
        }

        return fieldReference;

    }

    public Atom getName() {
        return name;
    }

    public boolean isFinal() {
        return (eField.accessFlags & FINAL.getValue()) != 0;
    }

    public boolean isPrivate() {
        return (eField.accessFlags & PRIVATE.getValue()) != 0;
    }

    public boolean isProtected() {
        return (eField.accessFlags & PROTECTED.getValue()) != 0;
    }

    public boolean isPublic() {
        return (eField.accessFlags & PUBLIC.getValue()) != 0;
    }

    public boolean isStatic() {
        return (eField.accessFlags & STATIC.getValue()) != 0;
    }

    public IClass getDeclaringClass() {
        return myClass;
    }

    public boolean isVolatile() {
        return (eField.accessFlags & VOLATILE.getValue()) != 0;
    }

    public IClassHierarchy getClassHierarchy() {
        return myClass.getClassHierarchy();
    }

	@Override
	public Collection<Annotation> getAnnotations() {
		// TODO Auto-generated method stub
		return Collections.emptySet();
	}

}
