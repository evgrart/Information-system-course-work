package ru.itmo.is.poteryashki.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Files are outside static resources. Image decoding removes metadata and rejects active formats. */
@Service
public class PrivateFiles {
    public record Stored(String key,String type,long size) {}
    private final Path root;
    public PrivateFiles(@Value("${app.storage-root}") String root)throws IOException{this.root=Path.of(root).toAbsolutePath().normalize();Files.createDirectories(this.root);}
    public Stored image(MultipartFile file,String namespace)throws IOException{
        byte[] raw=bytes(file);String format;
        if(raw.length>=3&&(raw[0]&255)==255&&(raw[1]&255)==216&&(raw[2]&255)==255)format="jpeg";
        else if(raw.length>=8&&Arrays.equals(Arrays.copyOf(raw,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))format="png";
        else throw new IllegalArgumentException("Only JPEG and PNG are allowed");
        byte[] clean;
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(raw))){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException("Invalid image");
            var reader=readers.next();
            try{reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||(long)w*h>16_000_000)throw new IllegalArgumentException("Image too large");
                var output=new ByteArrayOutputStream();if(!ImageIO.write(reader.read(0),format,output))throw new IllegalArgumentException("Invalid image");clean=output.toByteArray();
            }finally{reader.dispose();}
        }catch(javax.imageio.IIOException e){throw new IllegalArgumentException("Invalid image data",e);}
        if(clean.length>5*1024*1024)throw new IllegalArgumentException("Image too large");
        return save(namespace,format.equals("jpeg")?"jpg":"png","image/"+format,clean);
    }
    public Stored evidence(MultipartFile file,long user)throws IOException{
        byte[] bytes=bytes(file);
        if(bytes.length>5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return save("permissions/"+user,"pdf","application/pdf",bytes);
        return image(file,"permissions/"+user);
    }
    private byte[] bytes(MultipartFile file)throws IOException{if(file.isEmpty()||file.getSize()>5*1024*1024)throw new IllegalArgumentException("Invalid file size");return file.getBytes();}
    private Stored save(String namespace,String extension,String type,byte[] bytes)throws IOException{
        if(!namespace.matches("(?:listings|permissions)/[1-9][0-9]*"))throw new IllegalArgumentException("Invalid namespace");
        String key=namespace+"/"+UUID.randomUUID()+"."+extension;Path target=path(key);Files.createDirectories(target.getParent());
        ensureParents(target);Files.write(target,bytes,StandardOpenOption.CREATE_NEW);return new Stored(key,type,bytes.length);
    }
    private Path path(String key)throws IOException{
        if(!key.matches("(?:listings|permissions)/[1-9][0-9]*/[a-zA-Z0-9._-]+"))throw new FileNotFoundException("Invalid object key");
        Path target=root.resolve(key).normalize();if(!target.startsWith(root))throw new FileNotFoundException();ensureParents(target);return target;
    }
    private void ensureParents(Path target)throws IOException{for(Path p=target;p!=null&&p.startsWith(root);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symlink storage path");}
    public byte[] read(String key)throws IOException{
        String demo=switch(key){case "demo/listings/1/umbrella.jpg"->"umbrella.jpg";case "demo/permissions/1.pdf"->"permission1.pdf";case "demo/permissions/2.pdf"->"permission2.pdf";default->null;};
        if(demo!=null){try(var in=getClass().getResourceAsStream("/private-demo/"+demo)){if(in==null)throw new FileNotFoundException();return in.readAllBytes();}}
        return Files.readAllBytes(path(key));
    }
    public void delete(String key)throws IOException{Files.deleteIfExists(path(key));}
}
