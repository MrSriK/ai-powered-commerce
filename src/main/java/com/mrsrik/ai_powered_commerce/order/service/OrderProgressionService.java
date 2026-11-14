package com.mrsrik.ai_powered_commerce.order.service;

import com.mrsrik.ai_powered_commerce.order.dto.ProgressionResultDto;

/**
 * Service orchestrating automated order progression across lifecycle stages (Requirements 12.2 to 12.9).
 */
public interface OrderProgressionService {

    /**
     * Advances eligible orders through confirmation, shipping, and delivery stages.
     * Not transactional; each order advances in an isolated transaction.
     */
    ProgressionResultDto advanceAll();
}
