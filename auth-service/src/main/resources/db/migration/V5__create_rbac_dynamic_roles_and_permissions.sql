-- Migration V5: Create dynamic roles, permissions, and role_permissions schema

-- 1. Table auth_roles
CREATE TABLE IF NOT EXISTS auth_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_auth_roles_name ON auth_roles(name);

-- 2. Table auth_permissions
CREATE TABLE IF NOT EXISTS auth_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_auth_permissions_category ON auth_permissions(category);
CREATE INDEX IF NOT EXISTS idx_auth_permissions_name ON auth_permissions(name);

-- 3. Table auth_role_permissions
CREATE TABLE IF NOT EXISTS auth_role_permissions (
    role_id UUID NOT NULL REFERENCES auth_roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES auth_permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_role_id ON auth_role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_perm_id ON auth_role_permissions(permission_id);

-- 4. Seed default roles
INSERT INTO auth_roles (name, description, is_system) VALUES
('USER', 'Người dùng tiêu chuẩn hệ thống', TRUE),
('ADMIN', 'Quản trị viên toàn quyền hệ thống', TRUE),
('MODERATOR', 'Điều hành viên nội dung và báo cáo', FALSE),
('SUPPORT', 'Nhân viên hỗ trợ người dùng và khiếu nại', FALSE)
ON CONFLICT (name) DO NOTHING;

-- 5. Seed default permissions
INSERT INTO auth_permissions (name, category, description) VALUES
('USER:READ', 'USER_MANAGEMENT', 'Xem thông tin người dùng'),
('USER:BAN', 'USER_MANAGEMENT', 'Khóa tài khoản người dùng vi phạm'),
('USER:UNBAN', 'USER_MANAGEMENT', 'Mở khóa tài khoản người dùng'),
('USER:ROLE_ASSIGN', 'USER_MANAGEMENT', 'Gán hoặc thu hồi vai trò của người dùng'),
('CONTENT:MODERATE', 'CONTENT_MODERATION', 'Truy cập trung tâm kiểm duyệt nội dung'),
('POST:DELETE', 'CONTENT_MODERATION', 'Xóa bài viết vi phạm'),
('COMMENT:DELETE', 'CONTENT_MODERATION', 'Xóa bình luận vi phạm'),
('STORY:DELETE', 'CONTENT_MODERATION', 'Xóa story vi phạm'),
('REPORT:VIEW', 'REPORT_MANAGEMENT', 'Xem danh sách báo cáo vi phạm'),
('REPORT:RESOLVE', 'REPORT_MANAGEMENT', 'Xử lý báo cáo vi phạm'),
('REPORT:DISMISS', 'REPORT_MANAGEMENT', 'Bác bỏ báo cáo vi phạm'),
('SUPPORT:TICKET_READ', 'SUPPORT', 'Xem ticket yêu cầu hỗ trợ'),
('SUPPORT:TICKET_MANAGE', 'SUPPORT', 'Quản lý và phản hồi ticket'),
('SUPPORT:USER_ASSIST', 'SUPPORT', 'Hỗ trợ trực tiếp tài khoản người dùng'),
('SYSTEM:SETTINGS_READ', 'SYSTEM', 'Xem cấu hình hệ thống'),
('SYSTEM:SETTINGS_WRITE', 'SYSTEM', 'Thay đổi cấu hình hệ thống'),
('AUDIT:VIEW', 'SYSTEM', 'Xem lịch sử kiểm toán hệ thống')
ON CONFLICT (name) DO NOTHING;

-- 6. Assign all permissions to ADMIN role
INSERT INTO auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth_roles r, auth_permissions p WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- 7. Assign permissions to MODERATOR role
INSERT INTO auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth_roles r, auth_permissions p
WHERE r.name = 'MODERATOR' AND p.name IN (
    'CONTENT:MODERATE', 'POST:DELETE', 'COMMENT:DELETE', 'STORY:DELETE',
    'REPORT:VIEW', 'REPORT:RESOLVE', 'REPORT:DISMISS', 'USER:READ', 'USER:BAN'
)
ON CONFLICT DO NOTHING;

-- 8. Assign permissions to SUPPORT role
INSERT INTO auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth_roles r, auth_permissions p
WHERE r.name = 'SUPPORT' AND p.name IN (
    'USER:READ', 'REPORT:VIEW', 'SUPPORT:TICKET_READ', 'SUPPORT:TICKET_MANAGE', 'SUPPORT:USER_ASSIST'
)
ON CONFLICT DO NOTHING;
