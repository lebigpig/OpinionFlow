package com.lespider.opinionflow.spider.repo

import com.lespider.opinionflow.spider.domain.SearchResult
import org.springframework.data.jpa.repository.JpaRepository

interface SearchResultRepository : JpaRepository<SearchResult, Long>