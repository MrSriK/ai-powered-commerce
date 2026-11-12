package com.mrsrik.mappingfixture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Deliberately broken entity used only by MappingSmokeTest to prove the check can fail.
 * It lives outside com.mrsrik.ai_powered_commerce so neither the smoke test's entity scan
 * nor the application's component/entity scan can pick it up.
 */
@Entity
@Table(name = "duplicate_column_fixture")
public class DuplicateColumnFixture {

    @Id
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "owner_id")
    private Long ownerIdAgain;
}
