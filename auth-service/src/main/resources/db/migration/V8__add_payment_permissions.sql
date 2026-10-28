-- Migration V8: Add payment management permissions

INSERT INTO auth_permissions (name, category, description) VALUES
('PAYMENT:READ', 'PAYMENT_MANAGEMENT', 'Xem danh sách và chi tiết giao dịch thanh toán'),
('PAYMENT:RECONCILE', 'PAYMENT_MANAGEMENT', 'Đối soát giao dịch thanh toán thủ công')
ON CONFLICT (name) DO NOTHING;

-- Assign both permissions to ADMIN role
INSERT INTO auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth_roles r, auth_permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('PAYMENT:READ', 'PAYMENT:RECONCILE')
ON CONFLICT DO NOTHING;
