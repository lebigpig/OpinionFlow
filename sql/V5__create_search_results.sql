CREATE TABLE search_results (
                                id INT PRIMARY KEY AUTO_INCREMENT,     -- 修正语法
                                query TEXT,
                                url TEXT NOT NULL,
                                title TEXT,
                                score DECIMAL(10,8),
                                published_date TIMESTAMP,
                                content TEXT,
                                raw_content TEXT,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- MySQL 用 CURRENT_TIMESTAMP
);