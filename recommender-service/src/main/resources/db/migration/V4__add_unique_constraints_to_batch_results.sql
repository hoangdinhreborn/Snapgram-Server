-- Đảm bảo tính Idempotency: mỗi bài viết chỉ có 1 dòng trending và mỗi cặp user chỉ có 1 dòng gợi ý

DELETE FROM recommender_trending_post a USING recommender_trending_post b
WHERE a.id <> b.id AND a.post_id = b.post_id AND a.calculated_at < b.calculated_at;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_recommender_trending_post_id'
    ) THEN
        ALTER TABLE recommender_trending_post
        ADD CONSTRAINT uq_recommender_trending_post_id UNIQUE (post_id);
    END IF;
END $$;

DELETE FROM recommender_user_recommendation a USING recommender_user_recommendation b
WHERE a.id <> b.id AND a.user_id = b.user_id AND a.recommended_user_id = b.recommended_user_id AND a.calculated_at < b.calculated_at;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_recommender_user_rec_pair'
    ) THEN
        ALTER TABLE recommender_user_recommendation
        ADD CONSTRAINT uq_recommender_user_rec_pair UNIQUE (user_id, recommended_user_id);
    END IF;
END $$;
