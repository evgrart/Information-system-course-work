package ru.itmo.is.tools;

import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.model.EventSeverity;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.sax.SAXResult;
import java.nio.file.*;
import java.io.*;

/** Landscape slides from one editable Markdown source, exported as HTML and PDF. */
final class Presentation {
    static void build(Path root)throws Exception{
        Path folder=root.resolve("docs/part4/presentation");String[] slides=Files.readString(folder.resolve("slides.md")).replace("\r\n","\n").split("\\n---\\n");
        var fo=new StringBuilder("<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" font-family=\"Times New Roman\" font-size=\"16pt\" line-height=\"22pt\"><fo:layout-master-set><fo:simple-page-master master-name=\"slide\" page-width=\"320mm\" page-height=\"180mm\" margin=\"14mm\"><fo:region-body/><fo:region-after extent=\"8mm\"/></fo:simple-page-master></fo:layout-master-set>");
        var html=new StringBuilder("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Потеряшки · Презентация</title><style>body{margin:0;background:#1c3438;color:#1c3438;font:22px/1.5 'Segoe UI',Arial,sans-serif}section{box-sizing:border-box;background:#f6f8f3;max-width:1200px;min-height:675px;margin:30px auto;padding:48px 60px;position:relative}h1{font-size:42px;margin:0 0 24px;line-height:1.2}p{margin:12px 0}img{display:block;max-width:100%;height:440px;object-fit:contain;margin:auto}a{color:#17685c}footer{position:absolute;bottom:20px;left:60px;right:60px;display:flex;justify-content:space-between;font-size:14px}@media(max-width:700px){section{margin:14px;padding:24px;min-height:600px}h1{font-size:30px}body{font-size:18px}img{height:auto;max-height:380px}footer{left:24px;right:24px}}@media print{body{background:white}section{width:100%;margin:0;break-after:page}footer a{display:none}}</style></head><body>");
        int n=0;
        for(String slide:slides){n++;html.append("<section id=\"s").append(n).append("\">");
            fo.append("<fo:page-sequence master-reference=\"slide\" force-page-count=\"no-force\"><fo:static-content flow-name=\"xsl-region-after\"><fo:block font-size=\"11pt\" color=\"#687c7b\">Потеряшки · ").append(n).append(" / ").append(slides.length).append("</fo:block></fo:static-content><fo:flow flow-name=\"xsl-region-body\">");
            for(var block:Markdown.parse(slide)){
                if(block.kind().equals("heading")){html.append("<h1>").append(Models.xml(block.text())).append("</h1>");fo.append("<fo:block font-size=\"28pt\" line-height=\"32pt\" font-weight=\"bold\" color=\"#17685c\" space-after=\"9mm\">").append(Models.xml(block.text())).append("</fo:block>");}
                else if(block.kind().equals("image")){
                    var match=java.util.regex.Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)").matcher(block.text());if(!match.find())throw new IllegalArgumentException("Invalid slide image");
                    String path=match.group(2);html.append("<img src=\"").append(Models.xml(path)).append("\" alt=\"").append(Models.xml(match.group(1))).append("\">");
                    fo.append("<fo:block text-align=\"center\"><fo:external-graphic src=\"url('").append(folder.resolve(path).normalize().toUri()).append("')\" content-width=\"scale-to-fit\" content-height=\"scale-to-fit\" width=\"290mm\" height=\"105mm\" scaling=\"uniform\"/></fo:block>");
                }else if(block.kind().equals("paragraph")||block.kind().equals("list")){
                    html.append("<p>").append(Models.xml(block.text())).append("</p>");fo.append("<fo:block space-after=\"5mm\">").append(Reports.inline(block.text(),true)).append("</fo:block>");
                }
            }
            html.append("<footer><span>Потеряшки · ").append(n).append(" / ").append(slides.length).append("</span><span><a href=\"#s").append(Math.max(1,n-1)).append("\">← Назад</a> · <a href=\"#s").append(Math.min(slides.length,n+1)).append("\">Далее →</a></span></footer></section>");fo.append("</fo:flow></fo:page-sequence>");
        }
        fo.append("</fo:root>");html.append("</body></html>");Files.writeString(folder.resolve("presentation.html"),html);
        var factory=Reports.factory(root);var agent=factory.newFOUserAgent();agent.setTitle("Потеряшки · Курсовая работа");agent.setAuthor("Евграфов Артём Андреевич");agent.setCreator("");agent.setProducer("");
        agent.getEventBroadcaster().addEventListener(e->{if(e.getSeverity()==EventSeverity.ERROR||e.getSeverity()==EventSeverity.FATAL||e.getEventID().contains("overflow"))throw new IllegalStateException("Slide layout: "+e.getEventID());});
        try(var out=Files.newOutputStream(folder.resolve("presentation.pdf"))){var fop=factory.newFop(MimeConstants.MIME_PDF,agent,out);TransformerFactory.newInstance().newTransformer().transform(new StreamSource(new StringReader(fo.toString())),new SAXResult(fop.getDefaultHandler()));Verification.require(fop.getResults().getPageCount()==slides.length,"Unexpected presentation pages");}
        System.out.println("Presentation: "+slides.length+" slides, HTML and PDF");
    }
}
