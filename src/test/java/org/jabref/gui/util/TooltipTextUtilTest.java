package org.jabref.gui.util;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.text.Text;

import org.jabref.gui.search.TextFlowEqualityHelper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipTextUtilTest {

    private String testText = "this is a test text";

    @Test
    void retrieveCorrectTextStyleNormal() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.NORMAL);
        String textStyle = "Regular";

        assertEquals(textStyle, text.getFont().getStyle());
    }

    @Test
    void stringRemainsTheSameAfterTransformationToNormal() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.NORMAL);

        assertEquals(testText, text.getText());
    }

    @Test
    void retrieveCorrectTextStyleBold() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.BOLD);
        String textStyle = "tooltip-text-bold";

        assertEquals(textStyle, text.getStyleClass().toString());
    }

    @Test
    void stringRemainsTheSameAfterTransformationToBold() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.BOLD);

        assertEquals(testText, text.getText());
    }

    @Test
    void retrieveCorrectTextStyleItalic() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.ITALIC);
        String textStyle = "tooltip-text-italic";

        assertEquals(textStyle, text.getStyleClass().toString());
    }

    @Test
    void stringRemainsTheSameAfterTransformationToItalic() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.ITALIC);

        assertEquals(testText, text.getText());
    }

    @Test
    void createTextMonospaced() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.MONOSPACED);
        assertEquals("tooltip-text-monospaced", text.getStyleClass().toString());
        assertEquals(testText, text.getText());
    }

    @Test
    void retrieveCorrectStyleMonospaced() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.MONOSPACED);
        String textStyle = "tooltip-text-monospaced";

        assertEquals(textStyle, text.getStyleClass().toString());
    }

    @Test
    void stringRemainsTheSameAfterTransformationToMonospaced() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.MONOSPACED);

        assertEquals(testText, text.getText());
    }

    @Test
    void transformTextToHtmlStringBold() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.BOLD);
        String htmlString = TooltipTextUtil.textToHtmlString(text);
        String expectedString = "<b>" + testText + "</b>";

        assertEquals(expectedString, htmlString);
    }

    @Test
    void transformTextToHtmlStringItalic() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.ITALIC);
        String htmlString = TooltipTextUtil.textToHtmlString(text);
        String expectedString = "<i>" + testText + "</i>";

        assertEquals(expectedString, htmlString);
    }

    @Test
    void transformTextToHtmlStringMonospaced() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.MONOSPACED);
        String htmlString = TooltipTextUtil.textToHtmlString(text);
        String expectedString = "<tt>" + testText + "</tt>";

        assertEquals(expectedString, htmlString);
    }

    @Test
    void transformTextToHtmlStringMonospacedBold() {
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.MONOSPACED);
        text.getStyleClass().add("tooltip-text-bold");
        String htmlString = TooltipTextUtil.textToHtmlString(text);
        String expectedString = "<b><tt>" + testText + "</tt></b>";

        assertEquals(expectedString, htmlString);
    }

    @Test
    void transformTextToHtmlStringWithLinebreaks() {
        String testText = "this\nis a\ntest text";
        Text text = TooltipTextUtil.createText(testText, TooltipTextUtil.TextType.NORMAL);
        String htmlString = TooltipTextUtil.textToHtmlString(text);
        String expectedString = "this<br>is a<br>test text";

        assertEquals(expectedString, htmlString);
    }

    @Test
    void formatToTextsNoReplacements() {
        List<Text> expectedTextList = new ArrayList<>();
        expectedTextList.add(TooltipTextUtil.createText("This search contains entries in which any field contains the regular expression "));
        String test = "This search contains entries in which any field contains the regular expression ";
        List<Text> textList = TooltipTextUtil.formatToTexts(test);

        assertTrue(TextFlowEqualityHelper.checkIfTextsEqualsExpectedTexts(expectedTextList, textList));
    }

    @Test
    void formatToTextsEnd() {
        List<Text> expectedTextList = new ArrayList<>();
        expectedTextList.add(TooltipTextUtil.createText("This search contains entries in which any field contains the regular expression "));
        expectedTextList.add(TooltipTextUtil.createText("replacing text", TooltipTextUtil.TextType.BOLD));
        String test = "This search contains entries in which any field contains the regular expression <b>%0</b>";
        List<Text> textList = TooltipTextUtil.formatToTexts(test, new TooltipTextUtil.TextReplacement("<b>%0</b>", "replacing text", TooltipTextUtil.TextType.BOLD));

        assertTrue(TextFlowEqualityHelper.checkIfTextsEqualsExpectedTexts(expectedTextList, textList));
    }

    @Test
    void formatToTextsBegin() {
        List<Text> expectedTextList = new ArrayList<>();
        expectedTextList.add(TooltipTextUtil.createText("replacing text", TooltipTextUtil.TextType.BOLD));
        expectedTextList.add(TooltipTextUtil.createText(" This search contains entries in which any field contains the regular expression"));
        String test = "<b>%0</b> This search contains entries in which any field contains the regular expression";
        List<Text> textList = TooltipTextUtil.formatToTexts(test, new TooltipTextUtil.TextReplacement("<b>%0</b>", "replacing text", TooltipTextUtil.TextType.BOLD));

        assertTrue(TextFlowEqualityHelper.checkIfTextsEqualsExpectedTexts(expectedTextList, textList));
    }

    @Test
    void formatToTextsMiddle() {
        List<Text> expectedTextList = new ArrayList<>();
        expectedTextList.add(TooltipTextUtil.createText("This search contains entries "));
        expectedTextList.add(TooltipTextUtil.createText("replacing text", TooltipTextUtil.TextType.BOLD));
        expectedTextList.add(TooltipTextUtil.createText(" in which any field contains the regular expression"));
        String test = "This search contains entries <b>%0</b> in which any field contains the regular expression";
        List<Text> textList = TooltipTextUtil.formatToTexts(test, new TooltipTextUtil.TextReplacement("<b>%0</b>", "replacing text", TooltipTextUtil.TextType.BOLD));

        assertTrue(TextFlowEqualityHelper.checkIfTextsEqualsExpectedTexts(expectedTextList, textList));
    }
}
