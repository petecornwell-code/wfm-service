package com.wfm.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method whose assertion proves TODAY's actual behaviour at a property this
 * codebase cannot yet represent correctly (BDAY-06). {@link #flippedBy()} names the requirement
 * ID whose implementation changes what this method asserts; {@link #to()} states, in one line,
 * what the assertion changes to once that requirement lands.
 *
 * <p>Retained at {@code RUNTIME} and targeted at {@code METHOD} so a validator can reflect over a
 * scenario class's declared methods and compare the marked set against a parsed registry, by
 * name, in both directions. A source comment cannot satisfy that reflection -- which is the whole
 * reason this is an annotation and not a Javadoc note: it is what stops a later phase flipping
 * one of these assertions without updating the registry, or leaving a registry entry behind after
 * the method it described was deleted or rewritten.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AssertsTodaysBehaviour {

    /** The requirement ID whose implementation changes what this method currently asserts. */
    String flippedBy();

    /** One line describing what the assertion changes to once {@link #flippedBy()} lands. */
    String to();
}
