package jo.joide.deps.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Parseur XML : éléments, attributs, entités, CDATA, défauts.
 */
class XmlTest {

    @Test
    void parsesElementsAttributesAndText() {
        Xml.Document doc = Xml.parse(
                "<?xml version=\"1.0\"?>\n"
                        + "<project xmlns=\"http://maven\">\n"
                        + "  <groupId>org.example</groupId>\n"
                        + "  <artifactId>lib</artifactId>\n"
                        + "  <parent>\n"
                        + "    <groupId>org.example</groupId>\n"
                        + "    <artifactId>root</artifactId>\n"
                        + "  </parent>\n"
                        + "</project>");
        assertTrue(doc.healthy());
        assertEquals("project", doc.root.name());
        assertEquals("org.example", doc.root.child("groupId").text());
        assertEquals("root", doc.root.child("parent").child("artifactId")
                .text());
        assertNull(doc.root.child("missing"));
    }

    @Test
    void decodesEntitiesAndCdata() {
        Xml.Document doc = Xml.parse(
                "<a attr=\"v &amp; w\">x &lt; y <![CDATA[ & raw ]]> z"
                        + " &#65;&#x42; </a>");
        assertTrue(doc.healthy());
        assertEquals("v & w", doc.root.attr("attr"));
        assertEquals("x < y  & raw  z AB", doc.root.text());
    }

    @Test
    void toleratesCommentsAndProcessingInstructions() {
        Xml.Document doc = Xml.parse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<!-- avant -->"
                        + "<root><!-- milieu --><?pi ignored?>"
                        + "<child/></root><!-- après -->");
        assertTrue(doc.healthy());
        assertEquals(1, doc.root.children().size());
        assertEquals("child", doc.root.children().get(0).name());
        assertEquals("", doc.root.children().get(0).text());
    }

    @Test
    void reportsMalformedDocuments() {
        Xml.Document unclosed = Xml.parse("<root><child></root>");
        assertTrue(!unclosed.healthy());

        Xml.Document mismatch = Xml.parse("<root></other>");
        assertTrue(!mismatch.healthy());

        Xml.Document empty = Xml.parse("   ");
        assertNull(empty.root);
        assertTrue(!empty.healthy());

        Xml.Document trailing = Xml.parse("<root/>débris");
        assertTrue(!trailing.healthy());
    }

    @Test
    void childrenByNameFilters() {
        Xml.Document doc = Xml.parse(
                "<deps><dependency>a</dependency><other/>"
                        + "<dependency>b</dependency></deps>");
        assertEquals(2, doc.root.children("dependency").size());
        assertEquals("a", doc.root.children("dependency").get(0).text());
        assertEquals(3, doc.root.children().size());
    }
}
