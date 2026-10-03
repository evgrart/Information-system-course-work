package ru.itmo.is.poteryashki.persistence;
import java.util.Optional;
import org.springframework.data.repository.Repository;
import ru.itmo.is.poteryashki.domain.Listing;
public interface ListingRepository extends Repository<Listing,Long> {
    Optional<Listing> findById(Long id);
}
