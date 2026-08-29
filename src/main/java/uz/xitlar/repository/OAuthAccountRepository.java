package uz.xitlar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.xitlar.entity.OAuthAccount;
import uz.xitlar.enums.OAuthProvider;

import java.util.Optional;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Integer> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
