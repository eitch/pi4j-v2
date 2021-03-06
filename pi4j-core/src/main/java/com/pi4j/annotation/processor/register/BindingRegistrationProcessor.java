package com.pi4j.annotation.processor.register;

/*-
 * #%L
 * **********************************************************************
 * ORGANIZATION  :  Pi4J
 * PROJECT       :  Pi4J :: LIBRARY  :: Java Library (CORE)
 * FILENAME      :  BindingRegistrationProcessor.java
 *
 * This file is part of the Pi4J project. More information about
 * this project can be found here:  https://pi4j.com/
 * **********************************************************************
 * %%
 * Copyright (C) 2012 - 2020 Pi4J
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.pi4j.annotation.AddMember;
import com.pi4j.annotation.AddMembers;
import com.pi4j.annotation.Register;
import com.pi4j.annotation.exception.AnnotationException;
import com.pi4j.context.Context;
import com.pi4j.io.IO;
import com.pi4j.io.binding.Binding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>PwmRegistrationProcessor class.</p>
 *
 * @author Robert Savage (<a href="http://www.savagehomeautomation.com">http://www.savagehomeautomation.com</a>)
 * @version $Id: $Id
 */
public class BindingRegistrationProcessor implements RegisterProcessor<Binding> {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /** {@inheritDoc} */
    @Override
    public boolean isEligible(Context context, Object instance, Register annotation, Field field) throws Exception {

        // make sure this field is of type 'Pwm'
        if(!Binding.class.isAssignableFrom(field.getType()))
            return false;

        // this processor can process this annotated instance
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public Binding process(Context context, Object instance, Register annotation, Field field) throws Exception {

        // make sure the field instance is null; we can only register our own dynamically created I/O instances
        if(field.get(instance) != null)
            throw new AnnotationException("This @Register annotated instance is not null; it must be NULL " +
                    "to register a new I/O instance.  If you just want to access an existing I/O instance, " +
                    "use the '@Inject(id)' annotation instead.");

        Class bindingClass = field.getType();


        try {
            Method creatorMethod = bindingClass.getMethod("newInstance");
            Binding newBinding = (Binding)creatorMethod.invoke(instance);

            // add group members
            List<AddMember> members = new ArrayList<>();

            // get single-member annotations
            if (field.isAnnotationPresent(AddMember.class)) {
                AddMember memberAnnotation  = field.getAnnotation(AddMember.class);
                members.add(memberAnnotation);
            }
            // get multi-member annotations
            if (field.isAnnotationPresent(AddMembers.class)) {
                AddMembers membersAnnotation  = field.getAnnotation(AddMembers.class);
                members.addAll(List.of(membersAnnotation.value()));
            }

            // process all members proposed for this object instance
            for(AddMember member : members){
                String [] ids = member.value();
                for(String id : ids) {
                    if (context.registry().exists(id)) {
                        IO io = context.registry().get(id);
                        // add IO member to binding as long as it supports the required group interface
                        newBinding.add(io);
                    } else {
                        throw new AnnotationException("This @AddMember annotated instance [" + member.value() + "]" +
                                "could not be located in the IO registry and could not be added to this binding " +
                                "<" + field.getDeclaringClass() + "::" + field.getName() + ">");
                    }
                }
            }

            // get annotated object class
            System.out.println(newBinding);

            // return the newly created binding
            return newBinding;
        }
        catch(NoSuchMethodException e){
            logger.error(e.getMessage(), e);
            throw new AnnotationException("This @Register annotated instance [" + field.getName() + "]" +
                    "is not declared using a Binding Interface with a 'newInstance()' static method." +
                    "<" + field.getDeclaringClass() + "::" + field.getName() + ">");
        }
    }
}
