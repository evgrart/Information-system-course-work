package ru.itmo.is.poteryashki;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import ru.itmo.is.poteryashki.web.PrivateFiles;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class PrivateFilesTest {
    @TempDir Path root;
    @Test void acceptsRealImageAndIgnoresUserFilename()throws Exception{
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"png",out);
        var files=new PrivateFiles(root.toString());var saved=files.image(new MockMultipartFile("file","../../unsafe.html","text/html",out.toByteArray()),"listings/1");
        assertTrue(saved.key().matches("listings/1/[a-f0-9-]+\\.png"));assertEquals("image/png",saved.type());assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(files.read(saved.key()))));
    }
    @Test void rejectsSvgEvenWithImageMime()throws Exception{var f=new PrivateFiles(root.toString());assertThrows(IllegalArgumentException.class,()->f.image(new MockMultipartFile("file","a.png","image/png","<svg onload='alert(1)'/>".getBytes()),"listings/1"));}
    @Test void rejectsTraversalAndOversize()throws Exception{var f=new PrivateFiles(root.toString());assertThrows(java.io.IOException.class,()->f.read("../secret"));assertThrows(IllegalArgumentException.class,()->f.image(new MockMultipartFile("file",new byte[5*1024*1024+1]),"listings/1"));}
    @Test void rejectsFakePdfAndNamespace()throws Exception{var f=new PrivateFiles(root.toString());assertThrows(IllegalArgumentException.class,()->f.evidence(new MockMultipartFile("file","<html>fake</html>".getBytes()),1));}
    @Test void rejectsTruncatedImageWithoutCreatingStorageObject()throws Exception{
        var files=new PrivateFiles(root.toString());
        for(byte[] raw:java.util.List.of(new byte[]{(byte)137,80,78,71,13,10,26,10},new byte[]{(byte)255,(byte)216,(byte)255}))
            assertThrows(IllegalArgumentException.class,()->files.image(new MockMultipartFile("file","broken.png","image/png",raw),"listings/1"));
        try(var paths=java.nio.file.Files.walk(root)){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }
}
