package zw.test.billpay.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.test.billpay.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByClientReference(String clientReference);
}
