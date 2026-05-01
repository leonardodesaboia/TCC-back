package com.allset.api.favorite.service;

import com.allset.api.favorite.domain.FavoriteProfessional;
import com.allset.api.favorite.dto.FavoriteProfessionalResponse;
import com.allset.api.favorite.dto.FavoriteStatusResponse;
import com.allset.api.favorite.exception.FavoriteProfessionalAlreadyExistsException;
import com.allset.api.favorite.exception.FavoriteProfessionalNotFoundException;
import com.allset.api.favorite.mapper.FavoriteProfessionalMapper;
import com.allset.api.favorite.repository.FavoriteProfessionalRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteProfessionalServiceImplTest {

    @Mock
    private FavoriteProfessionalRepository favoriteProfessionalRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteProfessionalMapper favoriteProfessionalMapper;

    @InjectMocks
    private FavoriteProfessionalServiceImpl favoriteProfessionalService;

    @Test
    void favoriteShouldCreateFavoriteForClient() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID favoriteId = UUID.randomUUID();
        FavoriteProfessional savedFavorite = FavoriteProfessional.builder()
                .id(favoriteId)
                .clientId(clientId)
                .professionalId(professionalId)
                .createdAt(Instant.now())
                .build();
        FavoriteProfessionalResponse response = new FavoriteProfessionalResponse(
                favoriteId,
                clientId,
                null,
                savedFavorite.getCreatedAt()
        );

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(buildUser(clientId, UserRole.client)));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(buildProfessional(professionalId)));
        when(favoriteProfessionalRepository.existsByClientIdAndProfessionalId(clientId, professionalId)).thenReturn(false);
        when(favoriteProfessionalRepository.save(org.mockito.ArgumentMatchers.any(FavoriteProfessional.class)))
                .thenReturn(savedFavorite);
        when(favoriteProfessionalMapper.toResponse(savedFavorite)).thenReturn(response);

        FavoriteProfessionalResponse result = favoriteProfessionalService.favorite(clientId, professionalId);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<FavoriteProfessional> captor = ArgumentCaptor.forClass(FavoriteProfessional.class);
        verify(favoriteProfessionalRepository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo(clientId);
        assertThat(captor.getValue().getProfessionalId()).isEqualTo(professionalId);
    }

    @Test
    void favoriteShouldRejectDuplicateFavorite() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(buildUser(clientId, UserRole.client)));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(buildProfessional(professionalId)));
        when(favoriteProfessionalRepository.existsByClientIdAndProfessionalId(clientId, professionalId)).thenReturn(true);

        assertThatThrownBy(() -> favoriteProfessionalService.favorite(clientId, professionalId))
                .isInstanceOf(FavoriteProfessionalAlreadyExistsException.class)
                .hasMessageContaining(clientId.toString())
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void favoriteShouldRequireClientRole() {
        UUID userId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(buildUser(userId, UserRole.professional)));

        assertThatThrownBy(() -> favoriteProfessionalService.favorite(userId, professionalId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("clientes");
    }

    @Test
    void favoriteShouldRequireExistingClient() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteProfessionalService.favorite(clientId, professionalId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(clientId.toString());
    }

    @Test
    void favoriteShouldRequireActiveProfessional() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(buildUser(clientId, UserRole.client)));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteProfessionalService.favorite(clientId, professionalId))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void statusShouldReturnFavoriteFlag() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(buildUser(clientId, UserRole.client)));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(buildProfessional(professionalId)));
        when(favoriteProfessionalRepository.existsByClientIdAndProfessionalId(clientId, professionalId)).thenReturn(true);

        FavoriteStatusResponse result = favoriteProfessionalService.status(clientId, professionalId);

        assertThat(result.professionalId()).isEqualTo(professionalId);
        assertThat(result.favorite()).isTrue();
    }

    @Test
    void unfavoriteShouldDeleteExistingFavorite() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        FavoriteProfessional favorite = FavoriteProfessional.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .professionalId(professionalId)
                .build();

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(buildUser(clientId, UserRole.client)));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(buildProfessional(professionalId)));
        when(favoriteProfessionalRepository.findByClientIdAndProfessionalId(clientId, professionalId))
                .thenReturn(Optional.of(favorite));

        favoriteProfessionalService.unfavorite(clientId, professionalId);

        verify(favoriteProfessionalRepository).delete(favorite);
    }

    @Test
    void unfavoriteShouldFailWhenFavoriteDoesNotExist() {
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(buildUser(clientId, UserRole.client)));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(buildProfessional(professionalId)));
        when(favoriteProfessionalRepository.findByClientIdAndProfessionalId(clientId, professionalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteProfessionalService.unfavorite(clientId, professionalId))
                .isInstanceOf(FavoriteProfessionalNotFoundException.class)
                .hasMessageContaining(clientId.toString())
                .hasMessageContaining(professionalId.toString());
    }

    private User buildUser(UUID userId, UserRole role) {
        User user = User.builder()
                .name("Usuario Teste")
                .cpf("12345678901")
                .cpfHash("a".repeat(64))
                .email(userId + "@example.com")
                .phone("85999999999")
                .password("senha-hash")
                .role(role)
                .build();
        user.setId(userId);
        return user;
    }

    private Professional buildProfessional(UUID professionalId) {
        Professional professional = Professional.builder()
                .userId(UUID.randomUUID())
                .build();
        professional.setId(professionalId);
        return professional;
    }
}
