package ru.itmo.is.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;

/** Explicitly labelled fixture illustration and sample evidence; no real legal documents. */
final class DemoAssets {
    static void generate(Path root)throws Exception{
        Path folder=root.resolve("backend/src/main/resources/private-demo");Files.createDirectories(folder);
        var image=new BufferedImage(720,500,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(236,240,226));g.fillRect(0,0,720,500);
        g.setColor(new Color(29,79,70));g.fillArc(170,80,380,300,0,180);g.setStroke(new BasicStroke(12));g.drawLine(360,190,360,355);g.drawArc(295,325,65,75,180,180);
        g.setFont(new Font("Arial",Font.PLAIN,20));g.drawString("Учебная иллюстрация: зонт",190,450);g.dispose();ImageIO.write(image,"JPEG",folder.resolve("umbrella.jpg").toFile());
        for(int id: new int[]{1,2})try(var pdf=new PDDocument()){
            var page=new PDPage();pdf.addPage(page);try(var out=new PDPageContentStream(pdf,page)){out.beginText();out.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),16);out.newLineAtOffset(50,730);out.showText("COURSEWORK SAMPLE / NOT A LEGAL DOCUMENT");out.newLineAtOffset(0,-35);out.showText("Fixture permission " + id + ": synthetic evidence for demonstration.");out.endText();}pdf.save(folder.resolve("permission"+id+".pdf").toFile());
        }
    }
}
