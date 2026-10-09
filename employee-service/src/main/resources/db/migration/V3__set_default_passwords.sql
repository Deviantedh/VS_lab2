-- ===================================================================
-- V3__set_default_passwords.sql
-- Установка начальных хэшей паролей (BCrypt) для пользователей:
-- - admin: 'admin123'
-- - остальные пользователи: 'password123'
-- ===================================================================

UPDATE users
SET password_hash = '$2a$10$NKRdBGeNVMA3bSBCPWDUruunyfdT6uvdeZqBd0C6LwrPNixLElGEW'
WHERE login = 'admin' AND password_hash IS NULL;

UPDATE users
SET password_hash = '$2a$10$rRn4ru9rfj0tAPzuxEf4quf1f5F0BHjxyGn7YNEFe4FqJDPdKlbQC'
WHERE password_hash IS NULL;
