package com.luany.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens_recuperacion_password",
        indexes = @Index(name = "idx_token_recuperacion_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
public class RecuperacionPasswordToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_token_recuperacion_usuario"))
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiraEn;

    @Column(name = "utilizado_en", columnDefinition = "DATETIME(6)")
    private LocalDateTime utilizadoEn;

    @Column(name = "creado_en", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime creadoEn;
}
