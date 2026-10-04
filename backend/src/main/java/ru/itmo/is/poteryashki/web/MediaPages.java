package ru.itmo.is.poteryashki.web;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import java.io.IOException;

@RestController @RequiredArgsConstructor
public class MediaPages {
    private final WebQueries queries;private final PrivateFiles files;
    @GetMapping("/media/photos/{id}") ResponseEntity<byte[]> photo(@AuthenticationPrincipal WebIdentity me,@PathVariable long id)throws IOException{
        var photo=queries.photo(me.userId(),id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(photo.get("media_type").toString())).body(files.read(photo.get("object_key").toString()));
    }
    @GetMapping("/media/permissions/{id}") ResponseEntity<byte[]> evidence(@AuthenticationPrincipal WebIdentity me,@PathVariable long id)throws IOException{
        var row=queries.evidence(me.userId(),id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("Content-Disposition","attachment; filename=\"evidence\"").body(files.read(row.get("evidence_object_key").toString()));
    }
}
