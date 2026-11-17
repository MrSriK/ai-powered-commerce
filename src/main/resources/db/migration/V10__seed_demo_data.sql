-- V10: Seed demo reference data (categories, products, product images).
-- User-independent catalog data only. No user rows or user-scoped foreign keys are seeded,
-- as real user creation (including admin) is performed through POST /api/auth/signup with Auth0.
-- Product image URLs reference local files stored under the images/ directory in the project root.

-- 1. Categories (inferred from image file names in images/)
INSERT INTO categories (id, name) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Cameras'),
    ('10000000-0000-0000-0000-000000000002', 'Audio'),
    ('10000000-0000-0000-0000-000000000003', 'Personal Care'),
    ('10000000-0000-0000-0000-000000000004', 'Skincare'),
    ('10000000-0000-0000-0000-000000000005', 'Footwear');

-- 2. Products
INSERT INTO products (id, category_id, name, description, brand, sku, cost_price, selling_price, stock, is_active) VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'ProShot Mirrorless Digital Camera',
     'High-resolution mirrorless digital camera featuring 4K video recording, 24.2 MP sensor, and optical image stabilization.',
     'LumixPro', 'CAM-PRO-001', 450.00, 699.99, 25, TRUE),

    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'SonicWave Wireless Earbuds',
     'True wireless noise-cancelling earbuds with high-fidelity acoustics, 32-hour battery life, and IPX5 water resistance.',
     'AcoustiQ', 'AUD-EAR-001', 35.00, 79.99, 100, TRUE),

    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003',
     'DentaSonic Electric Toothbrush',
     'Rechargeable sonic electric toothbrush with 5 cleaning modes, smart pressure sensor, and 2-minute quad-pacer timer.',
     'OralGlow', 'PER-DNT-001', 20.00, 49.99, 80, TRUE),

    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004',
     'HydraSilk Daily Face Moisturizer',
     'Ultra-hydrating daily facial moisturizer formulated with hyaluronic acid and ceramides for 24-hour moisture barrier repair.',
     'DermaPure', 'SKN-MOI-001', 8.50, 24.99, 150, TRUE),

    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005',
     'AeroStride Running Shoes',
     'Lightweight, breathable road running shoes with responsive foam cushioning and high-traction rubber outsole.',
     'SwiftMotion', 'FTW-RUN-001', 32.00, 89.99, 60, TRUE);

-- 3. Product Images (display_order = 1 for primary image, referencing local images/ path)
INSERT INTO product_images (id, product_id, display_order, image_url) VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 1, 'images/camera.jpg'),
    ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 1, 'images/earbuds.jpg'),
    ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 1, 'images/electrictoothbrush.jpg'),
    ('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000004', 1, 'images/moisturizer.webp'),
    ('30000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000005', 1, 'images/shoes.jpg');
