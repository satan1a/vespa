// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class NegationTest {

    @Test
    public void requireThatNegationIsAnOperator() {
        assertTrue(PredicateOperator.class.isAssignableFrom(Negation.class));
    }

    @Test
    public void requireThatAccessorsWork() {
        Predicate foo = SimplePredicates.newString("foo");
        Negation node = new Negation(foo);
        assertSame(foo, node.getOperand());

        Predicate bar = SimplePredicates.newString("bar");
        node.setOperand(bar);
        assertSame(bar, node.getOperand());
    }

    @Test
    public void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        Negation node1 = new Negation(SimplePredicates.newString("a"));
        Negation node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
        assertNotSame(node1.getOperand(), node2.getOperand());
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        Predicate predicate = SimplePredicates.newPredicate();
        assertEquals(new Negation(predicate).hashCode(), new Negation(predicate).hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        Negation lhs = new Negation(SimplePredicates.newString("foo"));
        assertTrue(lhs.equals(lhs));
        assertFalse(lhs.equals(new Object()));

        Negation rhs = new Negation(SimplePredicates.newString("bar"));
        assertFalse(lhs.equals(rhs));
        rhs.setOperand(SimplePredicates.newString("foo"));
        assertTrue(lhs.equals(rhs));
    }

    @Test
    public void requireThatChildIsMandatoryInConstructor() {
        try {
            new Negation(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("operand", e.getMessage());
        }
    }

    @Test
    public void requireThatChildIsMandatoryInSetter() {
        Predicate operand = SimplePredicates.newPredicate();
        Negation negation = new Negation(operand);
        try {
            negation.setOperand(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("operand", e.getMessage());
        }
        assertSame(operand, negation.getOperand());
    }

    @Test
    public void requireThatChildIsIncludedInToString() {
        assertEquals("not (foo)", new Negation(SimplePredicates.newString("foo")).toString());
    }

}
