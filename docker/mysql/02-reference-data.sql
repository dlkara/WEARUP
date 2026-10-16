-- - 일반 회원·브랜드·관리자 권한과 기존 구독 기준만 초기화
-- - 기존 회원·브랜드·결제 샘플은 가져오지 않음
INSERT INTO Authority (grade) VALUES ('USER'), ('BRAND'), ('ADMIN');

INSERT INTO Plan (type, price, count)
VALUES ('SILVER', 50000, 3), ('GOLD', 70000, 5), ('VIP', 100000, 10);
