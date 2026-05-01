package com.allset.api.order.service;

import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.PhotoType;
import com.allset.api.order.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    /** Cria pedido Express, notifica todos os profissionais próximos ao mesmo tempo. */
    OrderResponse createExpressOrder(UUID clientId, CreateExpressOrderRequest request);

    /** Cria pedido On Demand a partir de um serviço publicado por um profissional. */
    OrderResponse createOnDemandOrder(UUID clientId, CreateOnDemandOrderRequest request);

    /** Profissional aceita ou recusa um pedido On Demand pendente. */
    OrderResponse respondOnDemand(UUID orderId, UUID professionalId, boolean accepted);

    /** Retorna o pedido — acesso ao cliente dono, profissional do pedido ou admin. */
    OrderResponse getOrder(UUID orderId, UUID requesterId, String requesterRole);

    /** Lista pedidos do usuário filtrados por status (opcional). */
    Page<OrderResponse> listOrders(UUID userId, String role, OrderStatus status, Pageable pageable);

    /** Lista pedidos Express pendentes em que o profissional foi notificado ou já enviou proposta e aguarda decisão. */
    Page<OrderResponse> listProfessionalExpressInbox(UUID userId, OrderStatus status, Pageable pageable);

    /** Retorna as propostas recebidas para um pedido Express (apenas cliente dono ou admin). */
    List<ExpressProposalResponse> getProposals(UUID orderId, UUID clientId, String requesterRole);

    /** Profissional aceita (com preço) ou recusa o pedido Express. */
    OrderResponse proRespond(UUID orderId, UUID professionalId, ProRespondRequest request);

    /** Cliente escolhe qual proposta aceitar pelo ID do profissional. As demais são recusadas automaticamente. */
    OrderResponse clientRespond(UUID orderId, UUID clientId, ClientRespondRequest request);

    /** Profissional marca como concluído e envia foto comprobatória (multipart). */
    OrderResponse completeByPro(UUID orderId, UUID professionalId, MultipartFile file);

    /** Cliente confirma conclusão — libera o escrow ao profissional. */
    OrderResponse confirmCompletion(UUID orderId, UUID clientId);

    /** Cancela o pedido (cliente ou profissional). */
    OrderResponse cancelOrder(UUID orderId, UUID requesterId, CancelOrderRequest request);

    /** Faz upload de uma foto vinculada ao pedido (request, completion_proof, etc.). */
    OrderPhotoResponse uploadPhoto(UUID orderId, UUID requesterUserId, String requesterRole,
                                   PhotoType type, MultipartFile file);

    /**
     * Chamado pelo scheduler:
     * - Marca timeout em profissionais que não responderam no prazo.
     * - Para pedidos sem nenhuma proposta após o prazo: expande raio ou cancela.
     * - Para pedidos com propostas onde cliente não escolheu no prazo: cancela.
     */
    void processExpiredWindows();
}
