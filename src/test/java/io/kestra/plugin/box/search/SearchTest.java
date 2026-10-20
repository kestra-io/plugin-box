package io.kestra.plugin.box.search;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.search.SearchForContentQueryParams;
import com.box.sdkgen.managers.search.SearchManager;
import com.box.sdkgen.schemas.filefull.FileFull;
import com.box.sdkgen.schemas.searchresultitem.SearchResultItem;
import com.box.sdkgen.schemas.searchresults.SearchResults;
import com.box.sdkgen.schemas.searchresultsresponse.SearchResultsResponse;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.box.models.FetchOutput;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
public class SearchTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeSearch extends Search {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    private static SearchResultItem file(String id, String name) {
        FileFull file = mock(FileFull.class);
        when(file.getId()).thenReturn(id);
        when(file.getName()).thenReturn(name);
        SearchResultItem item = mock(SearchResultItem.class);
        when(item.isFileFull()).thenReturn(true);
        when(item.getFileFull()).thenReturn(file);
        return item;
    }

    private static SearchResultsResponse page(long total, SearchResultItem... items) {
        return page(Long.valueOf(total), List.of(items));
    }

    private static SearchResultsResponse page(Long total, List<SearchResultItem> items) {
        SearchResults results = mock(SearchResults.class);
        when(results.getEntries()).thenReturn(items);
        when(results.getTotalCount()).thenReturn(total);
        SearchResultsResponse response = mock(SearchResultsResponse.class);
        when(response.getSearchResults()).thenReturn(results);
        return response;
    }

    private SearchManager search;

    private FakeSearch.FakeSearchBuilder<?, ?> task(SearchResultsResponse first, SearchResultsResponse... others) {
        search = mock(SearchManager.class);
        when(search.searchForContent(any(SearchForContentQueryParams.class))).thenReturn(first, others);
        BoxClient client = mock(BoxClient.class);
        when(client.getSearch()).thenReturn(search);
        return FakeSearch.builder().fakeClient(client).query(Property.ofValue("invoice"));
    }

    @Test
    void fetch_pagesByOffsetAndSendsFilters() throws Exception {
        var task = task(page(2, file("1", "a.pdf")), page(2, file("2", "b.pdf")))
            .fileExtensions(Property.ofValue(List.of("pdf")))
            .build();

        FetchOutput output = task.run(runContextFactory.of());

        assertThat(output.getSize(), is(2L));
        assertThat(output.getRows().get(1).getName(), is("b.pdf"));

        ArgumentCaptor<SearchForContentQueryParams> params = ArgumentCaptor.forClass(SearchForContentQueryParams.class);
        verify(search, times(2)).searchForContent(params.capture());
        assertThat(params.getAllValues().get(0).getQuery(), is("invoice"));
        assertThat(params.getAllValues().get(0).getFileExtensions(), is(List.of("pdf")));
        assertThat(params.getAllValues().get(0).getOffset(), is(0L));
        assertThat(params.getAllValues().get(1).getOffset(), is(1L));
    }

    @Test
    void missingTotalCount_stopsOnEmptyPage() throws Exception {
        var task = task(page((Long) null, List.of(file("1", "a.pdf"))), page((Long) null, List.of())).build();

        FetchOutput output = task.run(runContextFactory.of());

        assertThat(output.getSize(), is(1L));
        verify(search, times(2)).searchForContent(any(SearchForContentQueryParams.class));
    }

    @Test
    void stopsAtBoxOffsetCap() throws Exception {
        var task = task(page(50_000L)).build();
        // every call returns as many entries as the requested limit
        when(search.searchForContent(any(SearchForContentQueryParams.class))).thenAnswer(inv -> {
            long limit = inv.<SearchForContentQueryParams>getArgument(0).getLimit();
            return page(50_000L, LongStream.range(0, limit).mapToObj(i -> file("" + i, i + ".pdf")).toList());
        });

        FetchOutput output = task.run(runContextFactory.of());

        assertThat(output.getSize(), is(10_000L));
        ArgumentCaptor<SearchForContentQueryParams> params = ArgumentCaptor.forClass(SearchForContentQueryParams.class);
        verify(search, times(50)).searchForContent(params.capture());
        params.getAllValues().forEach(p -> assertThat(p.getOffset() + p.getLimit() <= 10_000L, is(true)));
    }

    @Test
    void unsetFiltersAreSentAsNull() throws Exception {
        var task = task(page(1, file("1", "a.pdf"))).build();

        task.run(runContextFactory.of());

        ArgumentCaptor<SearchForContentQueryParams> params = ArgumentCaptor.forClass(SearchForContentQueryParams.class);
        verify(search).searchForContent(params.capture());
        assertThat(params.getValue().getFileExtensions(), nullValue());
        assertThat(params.getValue().getOwnerUserIds(), nullValue());
    }

    @Test
    void fetchOne_stopsAfterFirstPage() throws Exception {
        var task = task(page(10, file("1", "a.pdf"))).fetchType(Property.ofValue(FetchType.FETCH_ONE)).build();

        FetchOutput output = task.run(runContextFactory.of());

        assertThat(output.getRow().getId(), is("1"));
        verify(search, times(1)).searchForContent(any(SearchForContentQueryParams.class));
    }
}
