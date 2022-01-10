// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Java representation of a template text.
 *
 * <p>A template is a sequence of literal text and dynamic sections defined by %{...} directives:</p>
 *
 * <pre>
 *     template: section*
 *     section: literal | variable | subform
 *     literal: plain text not containing %{
 *     variable: %{=identifier}
 *     subform: %{form identifier}template%{end}
 *     identifier: a valid Java identifier
 * </pre>
 *
 * <p>If the directive's end delimiter (}) is preceded by a "|" char, then any newline (\n)
 * following the end delimiter is removed.</p>
 *
 * <p>To use the template, <b>Instantiate</b> it to get a form ({@link #instantiate()}), fill it (e.g.
 * {@link Form#set(String, String) Form.set()}), and render the String ({@link Form#render()}).</p>
 *
 * <p>A form (like a template) has direct sections, and indirect sections in the body of direct subforms
 * sections (recursively). The variables that can be set for a form, are the variables defined in
 * either direct or indirect variable sections.</p>
 *
 * @see Form
 * @see TemplateParser
 * @see TemplateFile
 * @author hakonhall
 */
public class Template {
    private final Cursor start;
    private final Cursor end;

    private final List<Consumer<FormBuilder>> sections = new ArrayList<>();
    /** The value contains the location of the name of a sample variable section (with that name). */
    private final HashMap<String, Cursor> names = new HashMap<>();

    public static Template from(String text) { return from(text, new TemplateDescriptor()); }

    public static Template from(String text, TemplateDescriptor descriptor) {
        return TemplateParser.parse(text, descriptor).template();
    }

    Template(Cursor start) {
        this.start = new Cursor(start);
        this.end = new Cursor(start);
    }

    public Form instantiate() { return instantiate(null); }

    Form instantiate(Form parent) {
        return FormBuilder.build(parent, range(), sections);
    }

    void appendLiteralSection(Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        sections.add((FormBuilder builder) -> builder.addLiteralSection(range));
    }

    void appendVariableSection(String name, Cursor nameOffset, Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        sections.add(formBuilder -> formBuilder.addVariableSection(range, name, nameOffset));
    }

    void appendSubformSection(String name, Cursor nameCursor, Cursor end, Template body) {
        CursorRange range = verifyAndUpdateEnd(end);
        verifyNewName(name, nameCursor);
        sections.add(formBuilder -> formBuilder.addSubformSection(range, name, body));
    }

    private CursorRange range() { return new CursorRange(start, end); }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }

    private void verifyNewName(String name, Cursor cursor) {
        Cursor alreadyDefinedNameCursor = names.put(name, cursor);
        if (alreadyDefinedNameCursor != null) {
            throw new NameAlreadyExistsTemplateException(name, alreadyDefinedNameCursor, cursor);
        }
    }
}
