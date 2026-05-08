package com.allset.api.seed;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.repository.SubscriptionPlanRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductionSeedService {

    @Value("${seed.admin-email}")
    private String adminEmail;

    @Value("${seed.admin-password}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedResult seed() {
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return SeedResult.skipped(adminEmail);
        }

        seedAreas();
        seedSubscriptionPlans();
        seedAdminUser();

        return new SeedResult(false, adminEmail, 7, 15, 2);
    }

    private void seedAreas() {
        ServiceArea eletrica = saveArea("Eletrica", "catalog/areas/eletrica.png");
        ServiceArea limpeza = saveArea("Limpeza", "catalog/areas/limpeza.png");
        ServiceArea pintura = saveArea("Pintura", "catalog/areas/pintura.png");
        ServiceArea hidraulica = saveArea("Hidraulica", "catalog/areas/hidraulica.png");
        ServiceArea jardinagem = saveArea("Jardinagem", "catalog/areas/jardinagem.png");
        ServiceArea montagem = saveArea("Montagem", "catalog/areas/montagem.png");
        ServiceArea climatizacao = saveArea("Climatizacao", "catalog/areas/climatizacao.png");

        saveCategory(eletrica, "Eletricista", "catalog/categories/eletricista.png");
        saveCategory(eletrica, "Instalacao de luminarias", "catalog/categories/luminarias.png");

        saveCategory(limpeza, "Diarista", "catalog/categories/diarista.png");
        saveCategory(limpeza, "Limpeza pos-obra", "catalog/categories/limpeza-pos-obra.png");
        saveCategory(limpeza, "Passadoria", "catalog/categories/passadoria.png");

        saveCategory(pintura, "Pintor residencial", "catalog/categories/pintor.png");
        saveCategory(pintura, "Textura e acabamento", "catalog/categories/textura.png");

        saveCategory(hidraulica, "Encanador", "catalog/categories/encanador.png");
        saveCategory(hidraulica, "Desentupimento", "catalog/categories/desentupimento.png");

        saveCategory(jardinagem, "Jardineiro", "catalog/categories/jardineiro.png");
        saveCategory(jardinagem, "Podador", "catalog/categories/podador.png");

        saveCategory(montagem, "Montador de moveis", "catalog/categories/montador-moveis.png");
        saveCategory(montagem, "Instalador de persianas", "catalog/categories/persianas.png");

        saveCategory(climatizacao, "Tecnico em ar-condicionado", "catalog/categories/ar-condicionado.png");
        saveCategory(climatizacao, "Higienizacao de split", "catalog/categories/higienizacao-split.png");
    }

    private void seedSubscriptionPlans() {
        savePlan("Plano Pro", new BigDecimal("49.90"), "Pro");
        savePlan("Plano Destaque", new BigDecimal("79.90"), "Destaque");
    }

    private void seedAdminUser() {
        userRepository.save(User.builder()
                .name("Administrador")
                .cpf("00000000000")
                .cpfHash(sha256Hex("00000000000"))
                .email(adminEmail)
                .phone(null)
                .birthDate(null)
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.admin)
                .avatarUrl(null)
                .active(true)
                .notificationsEnabled(false)
                .banReason(null)
                .build());
    }

    private ServiceArea saveArea(String name, String iconKey) {
        return serviceAreaRepository.save(ServiceArea.builder()
                .name(name)
                .iconKey(iconKey)
                .active(true)
                .build());
    }

    private void saveCategory(ServiceArea area, String name, String iconKey) {
        serviceCategoryRepository.save(ServiceCategory.builder()
                .areaId(area.getId())
                .name(name)
                .iconKey(iconKey)
                .active(true)
                .build());
    }

    private void savePlan(String name, BigDecimal price, String badgeLabel) {
        subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name(name)
                .priceMonthly(price)
                .highlightInSearch(true)
                .expressPriority(true)
                .badgeLabel(badgeLabel)
                .active(true)
                .build());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel.", e);
        }
    }

    public record SeedResult(
            boolean skipped,
            String adminEmail,
            int areaCount,
            int categoryCount,
            int planCount
    ) {
        static SeedResult skipped(String adminEmail) {
            return new SeedResult(true, adminEmail, 0, 0, 0);
        }
    }
}
