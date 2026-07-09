ALTER TABLE books
    ADD COLUMN rating INTEGER;

ALTER TABLE books
    ADD CONSTRAINT chk_books_rating_range
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);
