package com.allset.api.seed;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.calendar.domain.BlockType;
import com.allset.api.calendar.domain.BlockedPeriod;
import com.allset.api.calendar.repository.BlockedPeriodRepository;
import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.domain.Message;
import com.allset.api.chat.domain.MessageType;
import com.allset.api.chat.repository.ConversationRepository;
import com.allset.api.chat.repository.MessageRepository;
import com.allset.api.dispute.domain.Dispute;
import com.allset.api.dispute.domain.DisputeEvidence;
import com.allset.api.dispute.domain.DisputeResolution;
import com.allset.api.dispute.domain.DisputeStatus;
import com.allset.api.dispute.domain.EvidenceType;
import com.allset.api.dispute.repository.DisputeEvidenceRepository;
import com.allset.api.dispute.repository.DisputeRepository;
import com.allset.api.document.domain.DocType;
import com.allset.api.document.domain.DocumentSide;
import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.domain.Platform;
import com.allset.api.notification.domain.PushToken;
import com.allset.api.notification.repository.NotificationRepository;
import com.allset.api.notification.repository.PushTokenRepository;
import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.order.domain.ClientResponse;
import com.allset.api.order.domain.ExpressQueueEntry;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderPhoto;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.OrderStatusHistory;
import com.allset.api.order.domain.PhotoType;
import com.allset.api.order.domain.ProResponse;
import com.allset.api.order.repository.ExpressQueueRepository;
import com.allset.api.order.repository.OrderPhotoRepository;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.order.repository.OrderStatusHistoryRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.ProfessionalSpecialty;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
import com.allset.api.review.domain.Review;
import com.allset.api.review.repository.ReviewRepository;
import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.repository.SubscriptionPlanRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StartupSeedService {

    private static final String DEFAULT_PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin.seed@allset.local";
    private static final String CLIENT_EMAIL = "cliente@email.com";

    private final UserRepository userRepository;
    private final SavedAddressRepository savedAddressRepository;
    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ProfessionalRepository professionalRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;
    private final ProfessionalDocumentRepository professionalDocumentRepository;
    private final ProfessionalOfferingRepository professionalOfferingRepository;
    private final BlockedPeriodRepository blockedPeriodRepository;
    private final OrderRepository orderRepository;
    private final OrderPhotoRepository orderPhotoRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ExpressQueueRepository expressQueueRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ReviewRepository reviewRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public SeedResult seed() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            return SeedResult.skipped(ADMIN_EMAIL, CLIENT_EMAIL, DEFAULT_PASSWORD);
        }

        Instant now = Instant.now();

        ServiceArea eletricaArea = saveServiceArea("Eletrica", "catalog/areas/eletrica.png");
        ServiceArea limpezaArea = saveServiceArea("Limpeza", "catalog/areas/limpeza.png");
        ServiceArea pinturaArea = saveServiceArea("Pintura", "catalog/areas/pintura.png");
        ServiceArea hidraulicaArea = saveServiceArea("Hidraulica", "catalog/areas/hidraulica.png");
        ServiceArea jardinagemArea = saveServiceArea("Jardinagem", "catalog/areas/jardinagem.png");
        ServiceArea montagemArea = saveServiceArea("Montagem", "catalog/areas/montagem.png");
        ServiceArea climatizacaoArea = saveServiceArea("Climatizacao", "catalog/areas/climatizacao.png");

        ServiceCategory eletricistaCategory = saveServiceCategory(eletricaArea, "Eletricista", "catalog/categories/eletricista.png");
        ServiceCategory luminariaCategory = saveServiceCategory(eletricaArea, "Instalacao de luminarias", "catalog/categories/luminarias.png");
        ServiceCategory diaristaCategory = saveServiceCategory(limpezaArea, "Diarista", "catalog/categories/diarista.png");
        ServiceCategory limpezaPosObraCategory = saveServiceCategory(limpezaArea, "Limpeza pos-obra", "catalog/categories/limpeza-pos-obra.png");
        ServiceCategory passadoriaCategory = saveServiceCategory(limpezaArea, "Passadoria", "catalog/categories/passadoria.png");
        ServiceCategory pintorCategory = saveServiceCategory(pinturaArea, "Pintor residencial", "catalog/categories/pintor.png");
        ServiceCategory texturaCategory = saveServiceCategory(pinturaArea, "Textura e acabamento", "catalog/categories/textura.png");
        ServiceCategory encanadorCategory = saveServiceCategory(hidraulicaArea, "Encanador", "catalog/categories/encanador.png");
        ServiceCategory desentupimentoCategory = saveServiceCategory(hidraulicaArea, "Desentupimento", "catalog/categories/desentupimento.png");
        ServiceCategory jardineiroCategory = saveServiceCategory(jardinagemArea, "Jardineiro", "catalog/categories/jardineiro.png");
        ServiceCategory podadorCategory = saveServiceCategory(jardinagemArea, "Podador", "catalog/categories/podador.png");
        ServiceCategory montadorMoveisCategory = saveServiceCategory(montagemArea, "Montador de moveis", "catalog/categories/montador-moveis.png");
        ServiceCategory persianaCategory = saveServiceCategory(montagemArea, "Instalador de persianas", "catalog/categories/persianas.png");
        ServiceCategory arCondicionadoCategory = saveServiceCategory(climatizacaoArea, "Tecnico em ar-condicionado", "catalog/categories/ar-condicionado.png");
        ServiceCategory higienizacaoSplitCategory = saveServiceCategory(climatizacaoArea, "Higienizacao de split", "catalog/categories/higienizacao-split.png");

        SubscriptionPlan proPlan = saveSubscriptionPlan("Plano Pro", new BigDecimal("49.90"), true, true, "Pro", true);
        SubscriptionPlan destaquePlan = saveSubscriptionPlan("Plano Destaque", new BigDecimal("79.90"), true, true, "Destaque", true);

        User clientUser = saveUser("Cliente Seed", "52998224725", CLIENT_EMAIL, "+5585999990001", LocalDate.of(1995, 9, 15), UserRole.client, true, null, "avatars/client-seed.jpg");
        User electricianUser = saveUser("Profissional Eletrica Seed", "11144477735", "eletrica@email", "+5585999990002", LocalDate.of(1990, 4, 20), UserRole.professional, true, null, "avatars/electrician-seed.jpg");
        User backupElectricianUser = saveUser("Profissional Backup Seed", "12345678909", "profissional.backup.seed@allset.local", "+5585999990003", LocalDate.of(1993, 8, 11), UserRole.professional, true, null, "avatars/backup-electrician-seed.jpg");
        User cleanerUser = saveUser("Profissional Limpeza Seed", "98765432100", "profissional.limpeza.seed@allset.local", "+5585999990004", LocalDate.of(1992, 1, 8), UserRole.professional, true, null, "avatars/cleaner-seed.jpg");
        User adminUser = saveUser("Admin Seed", "22233344455", ADMIN_EMAIL, "+5585999990005", LocalDate.of(1988, 12, 3), UserRole.admin, true, null, "avatars/admin-seed.jpg");
        User bannedUser = saveUser("Usuario Banido Seed", "33344455566", "banido.seed@allset.local", "+5585999990006", LocalDate.of(1991, 6, 27), UserRole.client, false, "Conta suspensa para testes administrativos", null);
        User plumberUser = saveUser("Profissional Hidraulica Seed", "44455566677", "profissional.hidraulica.seed@allset.local", "+5585999990007", LocalDate.of(1987, 5, 19), UserRole.professional, true, null, "avatars/plumber-seed.jpg");
        User painterPendingUser = saveUser("Profissional Pintura Pendente Seed", "55566677788", "profissional.pintura.pending.seed@allset.local", "+5585999990008", LocalDate.of(1994, 2, 14), UserRole.professional, true, null, "avatars/painter-pending-seed.jpg");
        User rejectedProfessionalUser = saveUser("Profissional Rejeitado Seed", "66677788899", "profissional.rejeitado.seed@allset.local", "+5585999990009", LocalDate.of(1989, 10, 9), UserRole.professional, true, null, "avatars/rejected-professional-seed.jpg");
        User gardenerUser = saveUser("Profissional Jardinagem Seed", "77788899900", "profissional.jardinagem.seed@allset.local", "+5585999990010", LocalDate.of(1986, 7, 21), UserRole.professional, true, null, "avatars/gardener-seed.jpg");
        User assemblerUser = saveUser("Profissional Montagem Seed", "88899900011", "profissional.montagem.seed@allset.local", "+5585999990011", LocalDate.of(1991, 11, 2), UserRole.professional, true, null, "avatars/assembler-seed.jpg");

        SavedAddress clientHome = saveAddress(
                clientUser,
                "Casa",
                "Rua Joaquim Nabuco",
                "150",
                "Apto 302",
                "Aldeota",
                "Fortaleza",
                "CE",
                "60125-121",
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                true
        );
        SavedAddress clientOffice = saveAddress(
                clientUser,
                "Escritorio",
                "Avenida Dom Luis",
                "500",
                "Sala 1201",
                "Meireles",
                "Fortaleza",
                "CE",
                "60160-230",
                new BigDecimal("-3.726112"),
                new BigDecimal("-38.499622"),
                false
        );

        Professional electricianProfessional = saveProfessional(
                electricianUser,
                "Eletricista residencial e comercial com foco em atendimentos express.",
                (short) 8,
                new BigDecimal("150.00"),
                VerificationStatus.approved,
                new BigDecimal("-3.729510"),
                new BigDecimal("-38.522975"),
                true,
                destaquePlan,
                now.plus(45, ChronoUnit.DAYS),
                null
        );
        Professional backupElectricianProfessional = saveProfessional(
                backupElectricianUser,
                "Atendimento eletrico para manutencao e reparos emergenciais.",
                (short) 5,
                new BigDecimal("130.00"),
                VerificationStatus.approved,
                new BigDecimal("-3.735010"),
                new BigDecimal("-38.540447"),
                true,
                proPlan,
                now.plus(30, ChronoUnit.DAYS),
                null
        );
        Professional cleanerProfessional = saveProfessional(
                cleanerUser,
                "Profissional de limpeza residencial com agenda flexivel.",
                (short) 6,
                new BigDecimal("95.00"),
                VerificationStatus.approved,
                new BigDecimal("-3.742014"),
                new BigDecimal("-38.498711"),
                true,
                null,
                null,
                null
        );
        Professional plumberProfessional = saveProfessional(
                plumberUser,
                "Encanador com foco em vazamentos, registros e manutencao emergencial.",
                (short) 9,
                new BigDecimal("160.00"),
                VerificationStatus.approved,
                new BigDecimal("-3.748115"),
                new BigDecimal("-38.515902"),
                false,
                proPlan,
                now.plus(60, ChronoUnit.DAYS),
                null
        );
        Professional painterPendingProfessional = saveProfessional(
                painterPendingUser,
                "Pintura residencial interna e pequenos reparos de acabamento.",
                (short) 7,
                new BigDecimal("110.00"),
                VerificationStatus.pending,
                null,
                null,
                false,
                null,
                null,
                null
        );
        Professional rejectedProfessional = saveProfessional(
                rejectedProfessionalUser,
                "Profissional em reenvio de documentacao para retomada do cadastro.",
                (short) 4,
                new BigDecimal("90.00"),
                VerificationStatus.rejected,
                null,
                null,
                false,
                null,
                null,
                "Documento ilegivel no verso. Reenviar em melhor qualidade."
        );
        Professional gardenerProfessional = saveProfessional(
                gardenerUser,
                "Jardineiro para manutencao de quintais, podas leves e organizacao de areas verdes.",
                (short) 10,
                new BigDecimal("115.00"),
                VerificationStatus.approved,
                new BigDecimal("-3.754912"),
                new BigDecimal("-38.489210"),
                false,
                null,
                null,
                null
        );
        Professional assemblerProfessional = saveProfessional(
                assemblerUser,
                "Montagem de moveis, instalacao de persianas e pequenos ajustes residenciais.",
                (short) 8,
                new BigDecimal("125.00"),
                VerificationStatus.approved,
                new BigDecimal("-3.721402"),
                new BigDecimal("-38.505418"),
                true,
                proPlan,
                now.plus(20, ChronoUnit.DAYS),
                null
        );

        saveSpecialty(electricianProfessional, eletricistaCategory, (short) 8, new BigDecimal("150.00"));
        saveSpecialty(electricianProfessional, encanadorCategory, (short) 4, new BigDecimal("140.00"));
        saveSpecialty(electricianProfessional, luminariaCategory, (short) 6, new BigDecimal("145.00"));
        saveSpecialty(backupElectricianProfessional, eletricistaCategory, (short) 5, new BigDecimal("130.00"));
        saveSpecialty(cleanerProfessional, diaristaCategory, (short) 6, new BigDecimal("95.00"));
        saveSpecialty(cleanerProfessional, limpezaPosObraCategory, (short) 5, new BigDecimal("105.00"));
        saveSpecialty(plumberProfessional, encanadorCategory, (short) 9, new BigDecimal("160.00"));
        saveSpecialty(plumberProfessional, desentupimentoCategory, (short) 7, new BigDecimal("170.00"));
        saveSpecialty(painterPendingProfessional, pintorCategory, (short) 7, new BigDecimal("110.00"));
        saveSpecialty(painterPendingProfessional, texturaCategory, (short) 3, new BigDecimal("125.00"));
        saveSpecialty(rejectedProfessional, diaristaCategory, (short) 4, new BigDecimal("90.00"));
        saveSpecialty(gardenerProfessional, jardineiroCategory, (short) 10, new BigDecimal("115.00"));
        saveSpecialty(gardenerProfessional, podadorCategory, (short) 8, new BigDecimal("120.00"));
        saveSpecialty(assemblerProfessional, montadorMoveisCategory, (short) 8, new BigDecimal("125.00"));
        saveSpecialty(assemblerProfessional, persianaCategory, (short) 6, new BigDecimal("118.00"));

        saveDocument(electricianProfessional, DocType.rg, DocumentSide.front, "documents/electrician-rg-front.jpg", true);
        saveDocument(electricianProfessional, DocType.rg, DocumentSide.back, "documents/electrician-rg-back.jpg", true);
        saveDocument(backupElectricianProfessional, DocType.cnh, DocumentSide.front, "documents/backup-electrician-cnh-front.jpg", true);
        saveDocument(backupElectricianProfessional, DocType.cnh, DocumentSide.back, "documents/backup-electrician-cnh-back.jpg", true);
        saveDocument(cleanerProfessional, DocType.rg, DocumentSide.front, "documents/cleaner-rg-front.jpg", true);
        saveDocument(cleanerProfessional, DocType.rg, DocumentSide.back, "documents/cleaner-rg-back.jpg", true);
        saveDocument(plumberProfessional, DocType.cnh, DocumentSide.front, "documents/plumber-cnh-front.jpg", true);
        saveDocument(plumberProfessional, DocType.cnh, DocumentSide.back, "documents/plumber-cnh-back.jpg", true);
        saveDocument(painterPendingProfessional, DocType.rg, DocumentSide.front, "documents/painter-pending-rg-front.jpg", false);
        saveDocument(painterPendingProfessional, DocType.rg, DocumentSide.back, "documents/painter-pending-rg-back.jpg", false);
        saveDocument(rejectedProfessional, DocType.cnh, DocumentSide.front, "documents/rejected-professional-cnh-front.jpg", false);
        saveDocument(rejectedProfessional, DocType.cnh, DocumentSide.back, "documents/rejected-professional-cnh-back.jpg", false);
        saveDocument(gardenerProfessional, DocType.rg, DocumentSide.front, "documents/gardener-rg-front.jpg", true);
        saveDocument(gardenerProfessional, DocType.rg, DocumentSide.back, "documents/gardener-rg-back.jpg", true);
        saveDocument(assemblerProfessional, DocType.cnh, DocumentSide.front, "documents/assembler-cnh-front.jpg", true);
        saveDocument(assemblerProfessional, DocType.cnh, DocumentSide.back, "documents/assembler-cnh-back.jpg", true);

        ProfessionalOffering electricianOffering = saveOffering(
                electricianProfessional,
                eletricistaCategory,
                "Instalacao e troca de tomadas",
                "Troca de tomadas, interruptores e pequenos ajustes de fiacao.",
                PricingType.fixed,
                new BigDecimal("180.00"),
                90,
                true
        );
        ProfessionalOffering electricianInspectionOffering = saveOffering(
                electricianProfessional,
                eletricistaCategory,
                "Diagnostico eletrico residencial",
                "Visita tecnica para identificar curto, sobrecarga e falhas intermitentes.",
                PricingType.hourly,
                null,
                60,
                true
        );
        ProfessionalOffering backupElectricianOffering = saveOffering(
                backupElectricianProfessional,
                eletricistaCategory,
                "Reparo eletrico emergencial",
                "Correcoes pontuais para quadros, disjuntores e tomadas queimadas.",
                PricingType.fixed,
                new BigDecimal("165.00"),
                80,
                true
        );
        ProfessionalOffering cleanerOffering = saveOffering(
                cleanerProfessional,
                diaristaCategory,
                "Limpeza pos-obra leve",
                "Limpeza focada em apartamentos e pequenos escritorios apos manutencao.",
                PricingType.fixed,
                new BigDecimal("220.00"),
                180,
                true
        );
        ProfessionalOffering plumberOffering = saveOffering(
                plumberProfessional,
                encanadorCategory,
                "Atendimento hidraulico emergencial",
                "Analise e correcao de vazamentos, registros e sifoes com atendimento rapido.",
                PricingType.hourly,
                null,
                60,
                true
        );
        ProfessionalOffering gardenerOffering = saveOffering(
                gardenerProfessional,
                jardineiroCategory,
                "Manutencao de jardim residencial",
                "Poda leve, limpeza de canteiros, adubacao basica e organizacao do jardim.",
                PricingType.fixed,
                new BigDecimal("190.00"),
                120,
                true
        );
        ProfessionalOffering assemblerOffering = saveOffering(
                assemblerProfessional,
                montadorMoveisCategory,
                "Montagem de moveis residenciais",
                "Montagem de armarios, racks, paineis e moveis comprados desmontados.",
                PricingType.fixed,
                new BigDecimal("210.00"),
                150,
                true
        );

        saveBlockedPeriodRecurring(electricianProfessional, (short) 1, LocalTime.of(8, 0), LocalTime.of(12, 0), "Agenda fixa de manha");
        saveBlockedPeriodSpecificDate(electricianProfessional, LocalDate.now().plusDays(5), LocalTime.of(13, 0), LocalTime.of(18, 0), "Treinamento presencial");

        pushTokenRepository.save(PushToken.builder()
                .userId(clientUser.getId())
                .expoToken("ExponentPushToken[client-seed-0001]")
                .platform(Platform.android)
                .lastSeen(now.minus(2, ChronoUnit.HOURS))
                .build());
        pushTokenRepository.save(PushToken.builder()
                .userId(electricianUser.getId())
                .expoToken("ExponentPushToken[electrician-seed-0001]")
                .platform(Platform.android)
                .lastSeen(now.minus(90, ChronoUnit.MINUTES))
                .build());
        pushTokenRepository.save(PushToken.builder()
                .userId(cleanerUser.getId())
                .expoToken("ExponentPushToken[cleaner-seed-0001]")
                .platform(Platform.ios)
                .lastSeen(now.minus(1, ChronoUnit.HOURS))
                .build());

        Order completedOrder = saveOrder(Order.builder()
                .clientId(clientUser.getId())
                .professionalId(cleanerProfessional.getId())
                .serviceId(cleanerOffering.getId())
                .areaId(limpezaArea.getId())
                .categoryId(diaristaCategory.getId())
                .mode(OrderMode.on_demand)
                .status(OrderStatus.completed)
                .description("Faxina completa apos reforma do escritorio")
                .addressId(clientOffice.getId())
                .addressSnapshot(addressSnapshot(clientOffice))
                .scheduledAt(now.minus(10, ChronoUnit.DAYS))
                .expiresAt(now.minus(11, ChronoUnit.DAYS))
                .proposalDeadline(now.minus(10, ChronoUnit.DAYS).minus(4, ChronoUnit.HOURS))
                .baseAmount(new BigDecimal("220.00"))
                .platformFee(new BigDecimal("44.00"))
                .totalAmount(new BigDecimal("220.00"))
                .proCompletedAt(now.minus(9, ChronoUnit.DAYS))
                .disputeDeadline(now.minus(8, ChronoUnit.DAYS))
                .completedAt(now.minus(8, ChronoUnit.DAYS))
                .build());

        saveOrderHistory(completedOrder, null, OrderStatus.pending, "Pedido seed criado", clientUser.getId(), now.minus(11, ChronoUnit.DAYS));
        saveOrderHistory(completedOrder, OrderStatus.pending, OrderStatus.accepted, "Profissional aceitou o pedido", cleanerUser.getId(), now.minus(10, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));
        saveOrderHistory(completedOrder, OrderStatus.accepted, OrderStatus.completed_by_pro, "Servico finalizado pelo profissional", cleanerUser.getId(), now.minus(9, ChronoUnit.DAYS));
        saveOrderHistory(completedOrder, OrderStatus.completed_by_pro, OrderStatus.completed, "Cliente confirmou a conclusao", clientUser.getId(), now.minus(8, ChronoUnit.DAYS));

        orderPhotoRepository.save(OrderPhoto.builder()
                .orderId(completedOrder.getId())
                .uploaderId(clientUser.getId())
                .photoType(PhotoType.request)
                .storageKey("order-photos/completed-request.jpg")
                .uploadedAt(now.minus(11, ChronoUnit.DAYS))
                .build());
        orderPhotoRepository.save(OrderPhoto.builder()
                .orderId(completedOrder.getId())
                .uploaderId(cleanerUser.getId())
                .photoType(PhotoType.completion_proof)
                .storageKey("order-photos/completed-proof.jpg")
                .uploadedAt(now.minus(9, ChronoUnit.DAYS))
                .build());

        saveBlockedPeriodOrder(
                cleanerProfessional,
                completedOrder.getId(),
                now.minus(10, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                now.minus(10, ChronoUnit.DAYS).plus(5, ChronoUnit.HOURS),
                "Atendimento confirmado"
        );

        Conversation completedConversation = saveConversation(completedOrder, cleanerUser.getId());
        saveTextMessage(completedConversation, clientUser.getId(), "Pode focar primeiro na sala de reuniao?", now.minus(10, ChronoUnit.DAYS).plus(10, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.DAYS).plus(11, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.DAYS).plus(20, ChronoUnit.MINUTES));
        saveTextMessage(completedConversation, cleanerUser.getId(), "Claro. Levo os produtos e confirmo ao chegar.", now.minus(10, ChronoUnit.DAYS).plus(14, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.DAYS).plus(15, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.DAYS).plus(25, ChronoUnit.MINUTES));
        saveSystemMessage(completedConversation, "Pedido concluido com sucesso.", now.minus(8, ChronoUnit.DAYS));

        reviewRepository.save(Review.builder()
                .orderId(completedOrder.getId())
                .reviewerId(clientUser.getId())
                .revieweeId(cleanerUser.getId())
                .rating((short) 5)
                .comment("Chegou no horario e deixou tudo organizado.")
                .submittedAt(now.minus(8, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES))
                .publishedAt(now.minus(8, ChronoUnit.DAYS).plus(40, ChronoUnit.MINUTES))
                .build());
        reviewRepository.save(Review.builder()
                .orderId(completedOrder.getId())
                .reviewerId(cleanerUser.getId())
                .revieweeId(clientUser.getId())
                .rating((short) 5)
                .comment(null)
                .submittedAt(now.minus(8, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES))
                .publishedAt(now.minus(8, ChronoUnit.DAYS).plus(40, ChronoUnit.MINUTES))
                .build());

        Order disputedOrder = saveOrder(Order.builder()
                .clientId(clientUser.getId())
                .professionalId(electricianProfessional.getId())
                .serviceId(electricianOffering.getId())
                .areaId(eletricaArea.getId())
                .categoryId(eletricistaCategory.getId())
                .mode(OrderMode.express)
                .status(OrderStatus.disputed)
                .description("Tomada da cozinha com cheiro de queimado")
                .addressId(clientHome.getId())
                .addressSnapshot(addressSnapshot(clientHome))
                .expiresAt(now.minus(3, ChronoUnit.DAYS).plus(40, ChronoUnit.MINUTES))
                .proposalDeadline(now.minus(3, ChronoUnit.DAYS).plus(15, ChronoUnit.MINUTES))
                .urgencyFee(new BigDecimal("20.00"))
                .baseAmount(new BigDecimal("180.00"))
                .platformFee(new BigDecimal("36.00"))
                .totalAmount(new BigDecimal("200.00"))
                .proCompletedAt(now.minus(2, ChronoUnit.DAYS))
                .disputeDeadline(now.plus(6, ChronoUnit.HOURS))
                .build());

        saveOrderHistory(disputedOrder, null, OrderStatus.pending, "Pedido Express seed criado", clientUser.getId(), now.minus(3, ChronoUnit.DAYS));
        saveOrderHistory(disputedOrder, OrderStatus.pending, OrderStatus.accepted, "Cliente escolheu proposta", clientUser.getId(), now.minus(3, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES));
        saveOrderHistory(disputedOrder, OrderStatus.accepted, OrderStatus.completed_by_pro, "Profissional informou conclusao", electricianUser.getId(), now.minus(2, ChronoUnit.DAYS));
        saveOrderHistory(disputedOrder, OrderStatus.completed_by_pro, OrderStatus.disputed, "Cliente abriu disputa", clientUser.getId(), now.minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));

        expressQueueRepository.save(ExpressQueueEntry.builder()
                .orderId(disputedOrder.getId())
                .professionalId(electricianProfessional.getId())
                .proposedAmount(new BigDecimal("180.00"))
                .notifiedAt(now.minus(3, ChronoUnit.DAYS))
                .respondedAt(now.minus(3, ChronoUnit.DAYS).plus(8, ChronoUnit.MINUTES))
                .proResponse(ProResponse.accepted)
                .clientResponse(ClientResponse.accepted)
                .clientRespondedAt(now.minus(3, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES))
                .queuePosition((short) 1)
                .distanceMeters(85)
                .build());
        expressQueueRepository.save(ExpressQueueEntry.builder()
                .orderId(disputedOrder.getId())
                .professionalId(backupElectricianProfessional.getId())
                .proposedAmount(new BigDecimal("195.00"))
                .notifiedAt(now.minus(3, ChronoUnit.DAYS))
                .respondedAt(now.minus(3, ChronoUnit.DAYS).plus(12, ChronoUnit.MINUTES))
                .proResponse(ProResponse.accepted)
                .clientResponse(ClientResponse.rejected)
                .clientRespondedAt(now.minus(3, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES))
                .queuePosition((short) 2)
                .distanceMeters(210)
                .build());

        orderPhotoRepository.save(OrderPhoto.builder()
                .orderId(disputedOrder.getId())
                .uploaderId(clientUser.getId())
                .photoType(PhotoType.request)
                .storageKey("order-photos/dispute-request.jpg")
                .uploadedAt(now.minus(3, ChronoUnit.DAYS))
                .build());
        orderPhotoRepository.save(OrderPhoto.builder()
                .orderId(disputedOrder.getId())
                .uploaderId(electricianUser.getId())
                .photoType(PhotoType.completion_proof)
                .storageKey("order-photos/dispute-proof.jpg")
                .uploadedAt(now.minus(2, ChronoUnit.DAYS))
                .build());

        saveBlockedPeriodOrder(
                electricianProfessional,
                disputedOrder.getId(),
                now.minus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                now.minus(3, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                "Visita tecnica vinculada ao pedido"
        );

        Conversation disputedConversation = saveConversation(disputedOrder, electricianUser.getId());
        saveSystemMessage(disputedConversation, "Pedido aceito. Voces podem conversar por aqui.", now.minus(3, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES));
        saveTextMessage(disputedConversation, clientUser.getId(), "A tomada voltou a falhar algumas horas depois.", now.minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), now.minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES), null);
        saveTextMessage(disputedConversation, electricianUser.getId(), "Posso retornar para revisar, mas vou registrar na disputa tambem.", now.minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).plus(12, ChronoUnit.MINUTES), now.minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).plus(13, ChronoUnit.MINUTES), null);
        saveSystemMessage(disputedConversation, "Disputa aberta pelo cliente. Motivo: problema persistente apos o reparo.", now.minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        saveSystemMessage(disputedConversation, "Disputa resolvida pelo admin com reembolso parcial.", now.minus(1, ChronoUnit.DAYS));

        Dispute dispute = disputeRepository.save(Dispute.builder()
                .orderId(disputedOrder.getId())
                .openedBy(clientUser.getId())
                .reason("O reparo nao resolveu o problema por completo.")
                .status(DisputeStatus.resolved)
                .resolution(DisputeResolution.refund_partial)
                .adminNotes("Profissional comprovou comparecimento, mas houve falha parcial no servico.")
                .clientRefundAmount(new BigDecimal("80.00"))
                .professionalAmount(new BigDecimal("120.00"))
                .resolvedBy(adminUser.getId())
                .resolvedAt(now.minus(1, ChronoUnit.DAYS))
                .openedAt(now.minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build());

        disputeEvidenceRepository.save(DisputeEvidence.builder()
                .disputeId(dispute.getId())
                .senderId(clientUser.getId())
                .evidenceType(EvidenceType.text)
                .content("A tomada voltou a apresentar faíscas no mesmo dia.")
                .sentAt(now.minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS).plus(5, ChronoUnit.MINUTES))
                .build());
        disputeEvidenceRepository.save(DisputeEvidence.builder()
                .disputeId(dispute.getId())
                .senderId(electricianUser.getId())
                .evidenceType(EvidenceType.photo)
                .fileKey("dispute-evidences/quadro-eletrico.jpg")
                .fileSizeBytes(182_400L)
                .fileMimeType("image/jpeg")
                .sentAt(now.minus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .build());
        disputeEvidenceRepository.save(DisputeEvidence.builder()
                .disputeId(dispute.getId())
                .senderId(adminUser.getId())
                .evidenceType(EvidenceType.text)
                .content("Resolucao parcial definida apos analise das evidencias das duas partes.")
                .sentAt(now.minus(1, ChronoUnit.DAYS))
                .build());

        Order pendingExpressOrder = saveOrder(Order.builder()
                .clientId(clientUser.getId())
                .areaId(eletricaArea.getId())
                .categoryId(eletricistaCategory.getId())
                .mode(OrderMode.express)
                .status(OrderStatus.pending)
                .description("Curto intermitente na iluminacao do corredor")
                .addressId(clientHome.getId())
                .addressSnapshot(addressSnapshot(clientHome))
                .expiresAt(now.plus(20, ChronoUnit.MINUTES))
                .proposalDeadline(now.minus(5, ChronoUnit.MINUTES))
                .urgencyFee(new BigDecimal("15.00"))
                .build());

        saveOrderHistory(pendingExpressOrder, null, OrderStatus.pending, "Pedido Express aguardando propostas", clientUser.getId(), now.minus(10, ChronoUnit.MINUTES));
        expressQueueRepository.save(ExpressQueueEntry.builder()
                .orderId(pendingExpressOrder.getId())
                .professionalId(electricianProfessional.getId())
                .proposedAmount(new BigDecimal("170.00"))
                .notifiedAt(now.minus(10, ChronoUnit.MINUTES))
                .respondedAt(now.minus(6, ChronoUnit.MINUTES))
                .proResponse(ProResponse.accepted)
                .queuePosition((short) 1)
                .distanceMeters(120)
                .build());
        expressQueueRepository.save(ExpressQueueEntry.builder()
                .orderId(pendingExpressOrder.getId())
                .professionalId(backupElectricianProfessional.getId())
                .notifiedAt(now.minus(10, ChronoUnit.MINUTES))
                .queuePosition((short) 2)
                .distanceMeters(275)
                .build());

        notificationRepository.save(Notification.builder()
                .userId(electricianUser.getId())
                .type(NotificationType.new_request)
                .title("Nova solicitacao Express")
                .body("Ha um novo pedido de eletrica aguardando proposta.")
                .data(orderData(pendingExpressOrder.getId()))
                .sentAt(now.minus(10, ChronoUnit.MINUTES))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(backupElectricianUser.getId())
                .type(NotificationType.request_rejected)
                .title("Proposta nao selecionada")
                .body("O cliente escolheu outro profissional para um pedido recente.")
                .data(orderData(disputedOrder.getId()))
                .sentAt(now.minus(3, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES))
                .readAt(now.minus(3, ChronoUnit.DAYS).plus(50, ChronoUnit.MINUTES))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(electricianUser.getId())
                .type(NotificationType.request_accepted)
                .title("Pedido aceito")
                .body("Seu orcamento foi aceito pelo cliente.")
                .data(orderData(disputedOrder.getId()))
                .sentAt(now.minus(3, ChronoUnit.DAYS).plus(35, ChronoUnit.MINUTES))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(clientUser.getId())
                .type(NotificationType.request_status_update)
                .title("Nova proposta recebida")
                .body("Voce recebeu uma proposta para o pedido Express em aberto.")
                .data(orderAndProfessionalData(pendingExpressOrder.getId(), electricianProfessional.getId()))
                .sentAt(now.minus(6, ChronoUnit.MINUTES))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(clientUser.getId())
                .type(NotificationType.new_message)
                .title("Nova mensagem")
                .body("O profissional enviou uma nova mensagem na conversa do pedido.")
                .data(conversationData(disputedOrder.getId(), disputedConversation.getId()))
                .sentAt(now.minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).plus(12, ChronoUnit.MINUTES))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(cleanerUser.getId())
                .type(NotificationType.payment_released)
                .title("Pagamento liberado")
                .body("O cliente confirmou a conclusao e o repasse esta pronto para liberacao.")
                .data(orderData(completedOrder.getId()))
                .sentAt(now.minus(8, ChronoUnit.DAYS))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(electricianUser.getId())
                .type(NotificationType.dispute_opened)
                .title("Disputa aberta")
                .body("O cliente abriu uma disputa para o pedido de eletrica.")
                .data(disputeData(dispute.getId(), disputedOrder.getId()))
                .sentAt(now.minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(clientUser.getId())
                .type(NotificationType.dispute_resolved)
                .title("Disputa resolvida")
                .body("A disputa foi encerrada com reembolso parcial.")
                .data(disputeData(dispute.getId(), disputedOrder.getId()))
                .sentAt(now.minus(1, ChronoUnit.DAYS))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(electricianUser.getId())
                .type(NotificationType.verification_result)
                .title("Perfil verificado")
                .body("Seu perfil profissional foi aprovado e esta apto para receber pedidos.")
                .data(objectMapper.createObjectNode().put("professionalId", electricianProfessional.getId().toString()))
                .sentAt(now.minus(20, ChronoUnit.DAYS))
                .readAt(now.minus(19, ChronoUnit.DAYS))
                .build());
        notificationRepository.save(Notification.builder()
                .userId(rejectedProfessionalUser.getId())
                .type(NotificationType.verification_result)
                .title("Documentacao rejeitada")
                .body("Seu cadastro profissional precisa de novo envio de documentos para continuar a analise.")
                .data(objectMapper.createObjectNode().put("professionalId", rejectedProfessional.getId().toString()))
                .sentAt(now.minus(4, ChronoUnit.DAYS))
                .build());

        log.info("event=startup_seed_banned_user email={} reason={}", bannedUser.getEmail(), bannedUser.getBanReason());
        log.info("event=startup_seed_extra_professionals approvedHydraulica={} pendingPintura={} rejected={} approvedJardinagem={} approvedMontagem={}",
                plumberProfessional.getId(),
                painterPendingProfessional.getId(),
                rejectedProfessional.getId(),
                gardenerProfessional.getId(),
                assemblerProfessional.getId());

        return new SeedResult(
                false,
                ADMIN_EMAIL,
                CLIENT_EMAIL,
                DEFAULT_PASSWORD,
                11,
                8,
                3,
                1,
                2
        );
    }

    private ServiceArea saveServiceArea(String name, String iconKey) {
        return serviceAreaRepository.save(ServiceArea.builder()
                .name(name)
                .iconKey(iconKey)
                .active(true)
                .build());
    }

    private ServiceCategory saveServiceCategory(ServiceArea area, String name, String iconKey) {
        return serviceCategoryRepository.save(ServiceCategory.builder()
                .areaId(area.getId())
                .name(name)
                .iconKey(iconKey)
                .active(true)
                .build());
    }

    private SubscriptionPlan saveSubscriptionPlan(
            String name,
            BigDecimal price,
            boolean highlightInSearch,
            boolean expressPriority,
            String badgeLabel,
            boolean active
    ) {
        return subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name(name)
                .priceMonthly(price)
                .highlightInSearch(highlightInSearch)
                .expressPriority(expressPriority)
                .badgeLabel(badgeLabel)
                .active(active)
                .build());
    }

    private User saveUser(
            String name,
            String cpf,
            String email,
            String phone,
            LocalDate birthDate,
            UserRole role,
            boolean active,
            String banReason,
            String avatarKey
    ) {
        return userRepository.save(User.builder()
                .name(name)
                .cpf(cpf)
                .cpfHash(sha256Hex(cpf))
                .email(email)
                .phone(phone)
                .birthDate(birthDate)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(role)
                .avatarUrl(avatarKey)
                .active(active)
                .notificationsEnabled(true)
                .banReason(banReason)
                .build());
    }

    private SavedAddress saveAddress(
            User user,
            String label,
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state,
            String zipCode,
            BigDecimal lat,
            BigDecimal lng,
            boolean isDefault
    ) {
        return savedAddressRepository.save(SavedAddress.builder()
                .userId(user.getId())
                .label(label)
                .street(street)
                .number(number)
                .complement(complement)
                .district(district)
                .city(city)
                .state(state)
                .zipCode(zipCode)
                .lat(lat)
                .lng(lng)
                .isDefault(isDefault)
                .build());
    }

    private Professional saveProfessional(
            User user,
            String bio,
            short yearsOfExperience,
            BigDecimal hourlyRate,
            VerificationStatus verificationStatus,
            BigDecimal geoLat,
            BigDecimal geoLng,
            boolean geoActive,
            SubscriptionPlan subscriptionPlan,
            Instant subscriptionExpiresAt,
            String rejectionReason
    ) {
        return professionalRepository.save(Professional.builder()
                .userId(user.getId())
                .bio(bio)
                .yearsOfExperience(yearsOfExperience)
                .baseHourlyRate(hourlyRate)
                .verificationStatus(verificationStatus)
                .rejectionReason(rejectionReason)
                .geoLat(geoLat)
                .geoLng(geoLng)
                .geoActive(geoActive)
                .subscriptionPlanId(subscriptionPlan != null ? subscriptionPlan.getId() : null)
                .subscriptionExpiresAt(subscriptionExpiresAt)
                .build());
    }

    private ProfessionalSpecialty saveSpecialty(
            Professional professional,
            ServiceCategory category,
            short yearsOfExperience,
            BigDecimal hourlyRate
    ) {
        return professionalSpecialtyRepository.save(ProfessionalSpecialty.builder()
                .professionalId(professional.getId())
                .categoryId(category.getId())
                .yearsOfExperience(yearsOfExperience)
                .hourlyRate(hourlyRate)
                .build());
    }

    private ProfessionalDocument saveDocument(
            Professional professional,
            DocType docType,
            DocumentSide docSide,
            String fileKey,
            boolean verified
    ) {
        return professionalDocumentRepository.save(ProfessionalDocument.builder()
                .professionalId(professional.getId())
                .docType(docType)
                .docSide(docSide)
                .fileKey(fileKey)
                .verified(verified)
                .build());
    }

    private ProfessionalOffering saveOffering(
            Professional professional,
            ServiceCategory category,
            String title,
            String description,
            PricingType pricingType,
            BigDecimal price,
            Integer estimatedDurationMinutes,
            boolean active
    ) {
        return professionalOfferingRepository.save(ProfessionalOffering.builder()
                .professionalId(professional.getId())
                .categoryId(category.getId())
                .title(title)
                .description(description)
                .pricingType(pricingType)
                .price(price)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .active(active)
                .build());
    }

    private void saveBlockedPeriodRecurring(
            Professional professional,
            short weekday,
            LocalTime startsAt,
            LocalTime endsAt,
            String reason
    ) {
        blockedPeriodRepository.save(BlockedPeriod.builder()
                .professionalId(professional.getId())
                .blockType(BlockType.recurring)
                .weekday(weekday)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .reason(reason)
                .build());
    }

    private void saveBlockedPeriodSpecificDate(
            Professional professional,
            LocalDate specificDate,
            LocalTime startsAt,
            LocalTime endsAt,
            String reason
    ) {
        blockedPeriodRepository.save(BlockedPeriod.builder()
                .professionalId(professional.getId())
                .blockType(BlockType.specific_date)
                .specificDate(specificDate)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .reason(reason)
                .build());
    }

    private void saveBlockedPeriodOrder(
            Professional professional,
            UUID orderId,
            Instant orderStartsAt,
            Instant orderEndsAt,
            String reason
    ) {
        blockedPeriodRepository.save(BlockedPeriod.builder()
                .professionalId(professional.getId())
                .blockType(BlockType.order)
                .orderId(orderId)
                .orderStartsAt(orderStartsAt)
                .orderEndsAt(orderEndsAt)
                .reason(reason)
                .build());
    }

    private Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    private void saveOrderHistory(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason,
            UUID changedBy,
            Instant createdAt
    ) {
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .changedBy(changedBy)
                .createdAt(createdAt)
                .build());
    }

    private Conversation saveConversation(Order order, UUID professionalUserId) {
        return conversationRepository.save(Conversation.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .professionalUserId(professionalUserId)
                .build());
    }

    private void saveTextMessage(
            Conversation conversation,
            UUID senderId,
            String content,
            Instant sentAt,
            Instant deliveredAt,
            Instant readAt
    ) {
        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .msgType(MessageType.text)
                .content(content)
                .sentAt(sentAt)
                .deliveredAt(deliveredAt)
                .readAt(readAt)
                .build());
    }

    private void saveSystemMessage(Conversation conversation, String content, Instant sentAt) {
        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .senderId(null)
                .msgType(MessageType.system)
                .content(content)
                .sentAt(sentAt)
                .build());
    }

    private JsonNode addressSnapshot(SavedAddress address) {
        return objectMapper.valueToTree(new AddressSnapshot(
                address.getLabel(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getDistrict(),
                address.getCity(),
                address.getState(),
                address.getZipCode(),
                address.getLat(),
                address.getLng()
        ));
    }

    private JsonNode orderData(UUID orderId) {
        return objectMapper.createObjectNode()
                .put("orderId", orderId.toString());
    }

    private JsonNode orderAndProfessionalData(UUID orderId, UUID professionalId) {
        return objectMapper.createObjectNode()
                .put("orderId", orderId.toString())
                .put("professionalId", professionalId.toString());
    }

    private JsonNode conversationData(UUID orderId, UUID conversationId) {
        return objectMapper.createObjectNode()
                .put("orderId", orderId.toString())
                .put("conversationId", conversationId.toString());
    }

    private JsonNode disputeData(UUID disputeId, UUID orderId) {
        return objectMapper.createObjectNode()
                .put("disputeId", disputeId.toString())
                .put("orderId", orderId.toString());
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

    private record AddressSnapshot(
            String label,
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state,
            String zipCode,
            BigDecimal lat,
            BigDecimal lng
    ) {}

    public record SeedResult(
            boolean skipped,
            String adminEmail,
            String clientEmail,
            String defaultPassword,
            int userCount,
            int professionalCount,
            int orderCount,
            int disputeCount,
            int conversationCount
    ) {
        static SeedResult skipped(String adminEmail, String clientEmail, String defaultPassword) {
            return new SeedResult(true, adminEmail, clientEmail, defaultPassword, 0, 0, 0, 0, 0);
        }
    }
}
