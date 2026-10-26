-- Thong tin provider khong con rieng cho VNPay (da co MoMo) -> doi ten cot cho dung nghia.
ALTER TABLE payment_orders RENAME COLUMN vnpay_response_code TO provider_response_code;
ALTER TABLE payment_orders RENAME COLUMN vnpay_bank_code TO provider_channel;
ALTER TABLE payment_orders RENAME COLUMN vnpay_pay_date TO provider_pay_date;

-- vnpay_transaction_no chi ghi khong doc va trung gia tri voi provider_transaction_no
-- (markSuccess luu ca 2) -> bo truong de khong con 2 cot gan nhu nhau.
ALTER TABLE payment_orders DROP COLUMN vnpay_transaction_no;
