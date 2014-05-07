package uk.co.flax.luwak.presearcher;/*
 * Copyright (c) 2013 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import uk.co.flax.luwak.InputDocument;
import uk.co.flax.luwak.Presearcher;
import uk.co.flax.luwak.termextractor.Extractor;
import uk.co.flax.luwak.termextractor.QueryTerm;
import uk.co.flax.luwak.termextractor.QueryTermExtractor;
import uk.co.flax.luwak.analysis.TermsEnumTokenStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Presearcher implementation that uses terms extracted from queries to index
 * them in the Monitor, and builds a BooleanQuery from InputDocuments to match
 * them.
 *
 * This Presearcher uses a QueryTermExtractor to extract terms from queries.
 */
public class TermFilteredPresearcher implements Presearcher {

    static {
        BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
    }

    public static final String TERMSFIELD = "_terms";

    private final QueryTermExtractor extractor;

    private final DocumentTokenFilter filter;

    public TermFilteredPresearcher(DocumentTokenFilter filter, Extractor... extractors) {
        this.extractor = new QueryTermExtractor(extractors);
        this.filter = filter;
    }

    public TermFilteredPresearcher(Extractor... extractors) {
        this(new DocumentTokenFilter.Default(), extractors);
    }

    @Override
    public final Query buildQuery(InputDocument doc) {
        try {
            AtomicReader reader = doc.asAtomicReader();
            BooleanQuery bq = new BooleanQuery();
            for (String field : reader.fields()) {

                boolean hasValues = false;

                TermsEnum te = reader.terms(field).iterator(null);
                TokenStream ts = filterInputDocumentTokens(field, new TermsEnumTokenStream(te));

                CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
                while (ts.incrementToken()) {
                    hasValues = true;
                    bq.add(new TermQuery(new Term(field, termAtt.toString())), BooleanClause.Occur.SHOULD);
                }
                if (hasValues)
                    bq.add(new TermQuery(new Term(field, extractor.getAnyToken())), BooleanClause.Occur.SHOULD);

            }
            return bq;
        }
        catch (IOException e) {
            // We're a MemoryIndex, so this shouldn't happen...
            throw new RuntimeException(e);
        }
    }

    protected TokenStream filterInputDocumentTokens(String field, TokenStream ts) {
        return this.filter.filter(field, ts);
    }

    public static final FieldType QUERYFIELDTYPE;
    static {
        QUERYFIELDTYPE = new FieldType(TextField.TYPE_STORED);
        QUERYFIELDTYPE.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        QUERYFIELDTYPE.freeze();
    }

    @Override
    public final Document indexQuery(Query query) {

        Map<String, StringBuilder> fieldTerms = new HashMap<>();
        for (QueryTerm queryTerm : extractor.extract(query)) {
            String term = (queryTerm.type == QueryTerm.Type.ANY) ? extractor.getAnyToken() : queryTerm.term;
            if (!fieldTerms.containsKey(queryTerm.field))
                fieldTerms.put(queryTerm.field, new StringBuilder());
            fieldTerms.get(queryTerm.field).append(" ").append(term);
        }

        Document doc = new Document();
        for (Map.Entry<String, StringBuilder> entry : fieldTerms.entrySet()) {
            doc.add(new Field(entry.getKey(), entry.getValue().toString(), QUERYFIELDTYPE));
        }

        return doc;
    }

}