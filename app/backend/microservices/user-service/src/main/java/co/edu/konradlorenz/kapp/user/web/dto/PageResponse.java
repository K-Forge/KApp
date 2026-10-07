package co.edu.konradlorenz.kapp.user.web.dto;

import java.util.List;

/**
 * One page of the directory, in the contract's {@code PageResponse} shape.
 *
 * <p>Page indexes are zero-based, so {@code first} is true at {@code page: 0}.
 */
public record PageResponse(
        List<DirectoryEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static PageResponse of(List<DirectoryEntry> content, int page, int size,
                                  long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                // Nothing matched: totalPages is 0, and the empty page a client holds is
                // both the first and the last one there will ever be.
                page >= totalPages - 1);
    }
}
