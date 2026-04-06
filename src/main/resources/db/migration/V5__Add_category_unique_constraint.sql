-- Add unique constraint on (user_id, name) for categories to prevent duplicate category names per user
-- First, remove duplicates if any exist (keep the one with the lowest id)
DELETE FROM categories c1
USING categories c2
WHERE c1.user_id = c2.user_id
  AND c1.name = c2.name
  AND c1.id > c2.id;

-- Add unique constraint
ALTER TABLE categories ADD CONSTRAINT uq_category_user_name UNIQUE (user_id, name);
