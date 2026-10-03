package ru.itmo.is.poteryashki.persistence;
import java.util.Optional;
import org.springframework.data.repository.Repository;
import ru.itmo.is.poteryashki.domain.User;
public interface UserRepository extends Repository<User,Long> {
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
}
