package com.stellarideas.grooves.smartplaylist;

import com.stellarideas.grooves.model.Genre;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SmartPlaylistQueryTranslatorTest {

    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    private final SmartPlaylistQueryTranslator translator = new SmartPlaylistQueryTranslator(
            Clock.fixed(NOW, ZoneOffset.UTC));

    // ---------- helpers ----------

    private static QueryExpr leaf(QueryPredicate p) { return new QueryExpr.Leaf(p); }
    private static QueryExpr and(QueryExpr... children) { return new QueryExpr.And(List.of(children)); }
    private static QueryExpr or(QueryExpr... children)  { return new QueryExpr.Or(List.of(children)); }
    private static QueryExpr not(QueryExpr child) { return new QueryExpr.Not(child); }

    private Document doc(QueryExpr expr, String userId) {
        return translator.translate(expr, userId).getCriteriaObject();
    }

    // ---------- base scope ----------

    @Test
    void emptyExpressionProducesOwnershipAndNotDeleted() {
        Criteria c = translator.translate(Optional.empty(), "user-1");
        Document d = c.getCriteriaObject();
        assertEquals("user-1", d.get("userId"));
        Document deleted = (Document) d.get("deleted");
        assertEquals(true, deleted.get("$ne"));
    }

    @Test
    void nullExpressionFallsBackToBase() {
        Document d = translator.translate((QueryExpr) null, "u").getCriteriaObject();
        assertEquals("u", d.get("userId"));
    }

    // ---------- flat single-leaf predicates ----------

    @Test
    void genreEqualityAddsGenreField() {
        Document d = doc(leaf(new QueryPredicate.GenreEq(Genre.THRASH_METAL)), "u");
        assertEquals(Genre.THRASH_METAL, d.get("genre"));
        assertEquals("u", d.get("userId"));
    }

    @Test
    void textContainsProducesCaseInsensitiveRegex() {
        Document d = doc(leaf(new QueryPredicate.TextContains(
                QueryPredicate.TextField.ARTIST, "AC/DC (Live)")), "u");
        Object clause = d.get("artist");
        assertInstanceOf(java.util.regex.Pattern.class, clause);
        java.util.regex.Pattern p = (java.util.regex.Pattern) clause;
        assertEquals(java.util.regex.Pattern.quote("AC/DC (Live)"), p.pattern());
        assertEquals(java.util.regex.Pattern.CASE_INSENSITIVE,
                p.flags() & java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    @Test
    void ratingRangeUsesGteAndLte() {
        Document d = doc(leaf(new QueryPredicate.IntRange(QueryPredicate.NumField.RATING, 3, 5)), "u");
        Document clause = (Document) d.get("rating");
        assertEquals(3, clause.get("$gte"));
        assertEquals(5, clause.get("$lte"));
    }

    @Test
    void yearRangeSerializesAsInt() {
        Document d = doc(leaf(new QueryPredicate.IntRange(QueryPredicate.NumField.YEAR, 1984, 1990)), "u");
        Document clause = (Document) d.get("year");
        assertEquals(1984, clause.get("$gte"));
        assertEquals(1990, clause.get("$lte"));
    }

    @Test
    void tagEqUsesExactMatch() {
        Document d = doc(leaf(new QueryPredicate.TagEq("acoustic")), "u");
        assertEquals("acoustic", d.get("customTags"));
    }

    @Test
    void lastPlayedBeforeUsesLtOnThreshold() {
        Document d = doc(leaf(new QueryPredicate.LastPlayedBefore(Duration.ofDays(180))), "u");
        Document clause = (Document) d.get("lastPlayedAt");
        assertEquals(NOW.minus(Duration.ofDays(180)), clause.get("$lt"));
    }

    @Test
    void lastPlayedSinceUsesGteOnThreshold() {
        Document d = doc(leaf(new QueryPredicate.LastPlayedSince(Duration.ofDays(30))), "u");
        Document clause = (Document) d.get("lastPlayedAt");
        assertEquals(NOW.minus(Duration.ofDays(30)), clause.get("$gte"));
    }

    // ---------- negation (flat) ----------

    @Test
    void notLeafProducesNorClauseAtTopLevel() {
        Document d = doc(not(leaf(new QueryPredicate.TagEq("skip"))), "u");
        assertEquals("u", d.get("userId"));
        @SuppressWarnings("unchecked")
        List<Document> nor = (List<Document>) d.get("$nor");
        assertNotNull(nor);
        assertEquals(1, nor.size());
        assertEquals("skip", nor.get(0).get("customTags"));
    }

    @Test
    void multipleNegationsShareOneNor() {
        Document d = doc(and(
                not(leaf(new QueryPredicate.TagEq("skip"))),
                not(leaf(new QueryPredicate.GenreEq(Genre.OTHER)))), "u");
        @SuppressWarnings("unchecked")
        List<Document> nor = (List<Document>) d.get("$nor");
        assertNotNull(nor);
        assertEquals(2, nor.size());
    }

    @Test
    void positiveAndNegativeCoexist() {
        Document d = doc(and(
                leaf(new QueryPredicate.GenreEq(Genre.HARD_ROCK)),
                not(leaf(new QueryPredicate.TagEq("skip")))), "u");
        assertEquals(Genre.HARD_ROCK, d.get("genre"));
        assertNotNull(d.get("$nor"));
    }

    @Test
    void multiplePredicatesAreAllPresent() {
        Document d = doc(and(
                leaf(new QueryPredicate.GenreEq(Genre.THRASH_METAL)),
                leaf(new QueryPredicate.IntCompare(QueryPredicate.NumField.RATING,
                        QueryPredicate.CompareOp.GTE, 4)),
                leaf(new QueryPredicate.TagEq("live"))), "u");
        assertEquals("u", d.get("userId"));
        assertEquals(Genre.THRASH_METAL, d.get("genre"));
        assertEquals("live", d.get("customTags"));
        Document rating = (Document) d.get("rating");
        assertEquals(4, rating.get("$gte"));
    }

    // ---------- OR (wrapped) ----------

    @Test
    void orAtRootWrapsBaseAndExpressionUnderAnd() {
        Document d = doc(or(
                leaf(new QueryPredicate.GenreEq(Genre.THRASH_METAL)),
                leaf(new QueryPredicate.GenreEq(Genre.HARD_ROCK))), "u");

        @SuppressWarnings("unchecked")
        List<Document> topAnd = (List<Document>) d.get("$and");
        assertNotNull(topAnd, "expected $and wrapper when OR appears at the root");
        assertEquals(2, topAnd.size());

        Document base = topAnd.get(0);
        assertEquals("u", base.get("userId"));

        Document orDoc = topAnd.get(1);
        @SuppressWarnings("unchecked")
        List<Document> orList = (List<Document>) orDoc.get("$or");
        assertNotNull(orList);
        assertEquals(2, orList.size());
        assertEquals(Genre.THRASH_METAL, orList.get(0).get("genre"));
        assertEquals(Genre.HARD_ROCK, orList.get(1).get("genre"));
    }

    @Test
    void andOfOrAndLeafWrapsCorrectly() {
        // (thrash OR hard_rock) AND rating:>=4
        Document d = doc(and(
                or(leaf(new QueryPredicate.GenreEq(Genre.THRASH_METAL)),
                   leaf(new QueryPredicate.GenreEq(Genre.HARD_ROCK))),
                leaf(new QueryPredicate.IntCompare(QueryPredicate.NumField.RATING,
                        QueryPredicate.CompareOp.GTE, 4))), "u");

        @SuppressWarnings("unchecked")
        List<Document> topAnd = (List<Document>) d.get("$and");
        assertNotNull(topAnd);
        // base + expression ($and of Or + rating)
        assertEquals(2, topAnd.size());
        assertEquals("u", topAnd.get(0).get("userId"));

        Document innerAnd = topAnd.get(1);
        @SuppressWarnings("unchecked")
        List<Document> andList = (List<Document>) innerAnd.get("$and");
        assertNotNull(andList);
        assertEquals(2, andList.size());
        assertNotNull(andList.get(0).get("$or"));
        assertNotNull(andList.get(1).get("rating"));
    }

    @Test
    void notOfOrUsesNorAroundInnerOr() {
        // NOT (tag:skip OR tag:demo)
        Document d = doc(not(or(
                leaf(new QueryPredicate.TagEq("skip")),
                leaf(new QueryPredicate.TagEq("demo")))), "u");

        @SuppressWarnings("unchecked")
        List<Document> topAnd = (List<Document>) d.get("$and");
        assertNotNull(topAnd);
        assertEquals(2, topAnd.size());

        Document negWrapper = topAnd.get(1);
        @SuppressWarnings("unchecked")
        List<Document> nor = (List<Document>) negWrapper.get("$nor");
        assertNotNull(nor);
        assertEquals(1, nor.size());

        @SuppressWarnings("unchecked")
        List<Document> innerOr = (List<Document>) nor.get(0).get("$or");
        assertNotNull(innerOr);
        assertEquals(2, innerOr.size());
    }

    // ---------- TextField mapping (ALBUM, TITLE) ----------

    @Test
    void textContainsMapsAlbumField() {
        Document d = doc(leaf(new QueryPredicate.TextContains(
                QueryPredicate.TextField.ALBUM, "Master of Puppets")), "u");
        assertInstanceOf(java.util.regex.Pattern.class, d.get("album"));
    }

    @Test
    void textContainsMapsTitleField() {
        Document d = doc(leaf(new QueryPredicate.TextContains(
                QueryPredicate.TextField.TITLE, "One")), "u");
        assertInstanceOf(java.util.regex.Pattern.class, d.get("title"));
    }

    @Test
    void textContainsEscapesRegexMetacharacters() {
        // Pattern.quote should neutralize all regex metacharacters; we verify a few would-be
        // metacharacters and confirm the literal value is preserved.
        String tricky = ".*+?(){}[]^$\\|";
        Document d = doc(leaf(new QueryPredicate.TextContains(
                QueryPredicate.TextField.TITLE, tricky)), "u");
        java.util.regex.Pattern p = (java.util.regex.Pattern) d.get("title");
        assertEquals(java.util.regex.Pattern.quote(tricky), p.pattern());
        // Sanity: the compiled pattern matches its literal input
        assertTrue(p.matcher("prefix " + tricky + " suffix").find());
    }

    // ---------- IntEq (missing in original suite) ----------

    @Test
    void intEqOnRatingUsesEquality() {
        Document d = doc(leaf(new QueryPredicate.IntEq(QueryPredicate.NumField.RATING, 4)), "u");
        assertEquals(4, d.get("rating"));
    }

    @Test
    void intEqOnYearSerializesAsInt() {
        Document d = doc(leaf(new QueryPredicate.IntEq(QueryPredicate.NumField.YEAR, 1986)), "u");
        assertEquals(1986, d.get("year"));
    }

    @Test
    void intEqOnPlayCountUsesIntValue() {
        Document d = doc(leaf(new QueryPredicate.IntEq(QueryPredicate.NumField.PLAY_COUNT, 10)), "u");
        assertEquals(10, d.get("playCount"));
    }

    // ---------- IntCompare (each operator + each numeric field) ----------

    @Test
    void intCompareGtOnPlayCountUsesGt() {
        Document d = doc(leaf(new QueryPredicate.IntCompare(
                QueryPredicate.NumField.PLAY_COUNT, QueryPredicate.CompareOp.GT, 5)), "u");
        Document clause = (Document) d.get("playCount");
        assertEquals(5, clause.get("$gt"));
    }

    @Test
    void intCompareLtOnRatingUsesLt() {
        Document d = doc(leaf(new QueryPredicate.IntCompare(
                QueryPredicate.NumField.RATING, QueryPredicate.CompareOp.LT, 3)), "u");
        Document clause = (Document) d.get("rating");
        assertEquals(3, clause.get("$lt"));
    }

    @Test
    void intCompareLteOnRatingUsesLte() {
        Document d = doc(leaf(new QueryPredicate.IntCompare(
                QueryPredicate.NumField.RATING, QueryPredicate.CompareOp.LTE, 2)), "u");
        Document clause = (Document) d.get("rating");
        assertEquals(2, clause.get("$lte"));
    }

    @Test
    void intCompareOnYearSerializesAsInt() {
        Document d = doc(leaf(new QueryPredicate.IntCompare(
                QueryPredicate.NumField.YEAR, QueryPredicate.CompareOp.GT, 1979)), "u");
        Document clause = (Document) d.get("year");
        assertEquals(1979, clause.get("$gt"));
    }

    // ---------- Base scope is preserved through wrapped (non-flat) path ----------

    @Test
    void orAtRootStillCarriesUserAndDeletedNeFilter() {
        Document d = doc(or(
                leaf(new QueryPredicate.GenreEq(Genre.THRASH_METAL)),
                leaf(new QueryPredicate.GenreEq(Genre.HARD_ROCK))), "u");

        @SuppressWarnings("unchecked")
        List<Document> topAnd = (List<Document>) d.get("$and");
        assertNotNull(topAnd);
        Document base = topAnd.get(0);
        assertEquals("u", base.get("userId"));
        Document deleted = (Document) base.get("deleted");
        assertNotNull(deleted, "soft-delete filter must survive the wrapped path");
        assertEquals(true, deleted.get("$ne"));
    }

    // ---------- Deeper nesting ----------

    @Test
    void orOfTwoAndsTranslatesAsOrOfAnds() {
        // (genre:THRASH_METAL AND rating:>=4) OR (genre:HARD_ROCK AND tag:live)
        Document d = doc(or(
                and(leaf(new QueryPredicate.GenreEq(Genre.THRASH_METAL)),
                    leaf(new QueryPredicate.IntCompare(QueryPredicate.NumField.RATING,
                            QueryPredicate.CompareOp.GTE, 4))),
                and(leaf(new QueryPredicate.GenreEq(Genre.HARD_ROCK)),
                    leaf(new QueryPredicate.TagEq("live")))), "u");

        @SuppressWarnings("unchecked")
        List<Document> topAnd = (List<Document>) d.get("$and");
        assertNotNull(topAnd);
        assertEquals(2, topAnd.size());

        Document orWrapper = topAnd.get(1);
        @SuppressWarnings("unchecked")
        List<Document> orList = (List<Document>) orWrapper.get("$or");
        assertNotNull(orList);
        assertEquals(2, orList.size());
        // Both branches are themselves $and clauses
        assertNotNull(orList.get(0).get("$and"));
        assertNotNull(orList.get(1).get("$and"));
    }

    @Test
    void doubleNegationProducesNestedNor() {
        // NOT (NOT tag:skip) — the outer NOT wraps an OR-or-NOT-or-And? Actually NOT(Leaf(...))
        // is flattenable. NOT(NOT(Leaf)) is not flattenable because the immediate child of the
        // outer Not is itself a Not, not a Leaf. Verify it goes through the wrapped path.
        Document d = doc(not(not(leaf(new QueryPredicate.TagEq("skip")))), "u");

        @SuppressWarnings("unchecked")
        List<Document> topAnd = (List<Document>) d.get("$and");
        assertNotNull(topAnd, "double negation should hit the wrapped path");
        assertEquals(2, topAnd.size());

        Document outer = topAnd.get(1);
        @SuppressWarnings("unchecked")
        List<Document> outerNor = (List<Document>) outer.get("$nor");
        assertNotNull(outerNor);
        assertEquals(1, outerNor.size());

        // Inner NOT translated to its own $nor
        @SuppressWarnings("unchecked")
        List<Document> innerNor = (List<Document>) outerNor.get(0).get("$nor");
        assertNotNull(innerNor);
        assertEquals("skip", innerNor.get(0).get("customTags"));
    }
}
