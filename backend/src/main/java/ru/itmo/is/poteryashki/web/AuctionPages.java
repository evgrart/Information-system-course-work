package ru.itmo.is.poteryashki.web;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import ru.itmo.is.poteryashki.service.AuctionService;
import java.math.BigDecimal;
import java.util.UUID;
import java.io.IOException;

@Controller @RequiredArgsConstructor
public class AuctionPages {
    private final WebQueries queries;private final AuctionService auctions;private final PrivateFiles files;
    @GetMapping("/auctions") String list(@AuthenticationPrincipal WebIdentity me,Model m){m.addAttribute("auctions",queries.auctions(me.userId()));m.addAttribute("permissions",queries.permissions(me.userId()));m.addAttribute("own",queries.ownListings(me.userId()));return "auctions";}
    @GetMapping("/auctions/{id}") String detail(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,Model m){m.addAttribute("auction",queries.auction(me.userId(),id));m.addAttribute("key",UUID.randomUUID());return "auction";}
    @PostMapping("/auctions/{id}/bids") String bid(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam BigDecimal amount,@RequestParam UUID key){auctions.bid(me.userId(),id,amount,key);return "redirect:/auctions/"+id;}
    @PostMapping("/auction-permissions") String request(@AuthenticationPrincipal WebIdentity me,@RequestParam long listing,@RequestParam String basis,@RequestParam MultipartFile file)throws IOException{
        queries.ownedListing(me.userId(),listing);var object=files.evidence(file,me.userId());
        try{auctions.requestPermission(me.userId(),listing,basis,object.key());}catch(RuntimeException e){files.delete(object.key());throw e;}return "redirect:/auctions";
    }
    @PostMapping("/auctions") String create(@AuthenticationPrincipal WebIdentity me,@RequestParam long listing,@RequestParam long permission,@RequestParam String start,@RequestParam String end,@RequestParam BigDecimal price,@RequestParam BigDecimal step){
        long id=auctions.create(me.userId(),listing,permission,UiText.time(start),UiText.time(end),price,step);return "redirect:/auctions/"+id;
    }
}
