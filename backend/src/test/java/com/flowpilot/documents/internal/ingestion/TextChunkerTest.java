 package com.flowpilot.documents.internal.ingestion;

 import static org.assertj.core.api.Assertions.assertThat;
 import static org.assertj.core.api.Assertions.assertThatThrownBy;

 import java.util.List;
 import java.util.stream.Collectors;
 import java.util.stream.IntStream;

 import org.junit.jupiter.api.Test;

 import com.flowpilot.documents.internal.ingestion.PdfTextExtractor.PageText;

 class TextChunkerTest {

     private static String words(int count) {
        return IntStream.range(0, count).mapToObj(i -> "word" + i).collect(Collectors.joining(" "));
     }

     @Test
     void chunk_shortText_returnsSingleChunk() {
         var chunks = TextChunker.chunk(List.of(new PageText(1, "Hello world.")), 1000, 100);

         assertThat(chunks).hasSize(1);
         assertThat(chunks.get(0).content()).isEqualTo("Hello world.");
         assertThat(chunks.get(0).pageFrom()).isEqualTo(1);
         assertThat(chunks.get(0).pageTo()).isEqualTo(1);
     }

     @Test
     void chunk_shortPages_trackFirstAndLastPage() {
         var chunks = TextChunker.chunk(
                 List.of(new PageText(1, "First page."), new PageText(2, "Second page.")), 1000, 100);

         assertThat(chunks).hasSize(1);
         assertThat(chunks.get(0).pageFrom()).isEqualTo(1);
         assertThat(chunks.get(0).pageTo()).isEqualTo(2);
     }

     @Test
     void chunk_longText_neverExceedsMaxChars() {
         var chunks = TextChunker.chunk(List.of(new PageText(1, words(2000))), 1000, 100);

         assertThat(chunks).hasSizeGreaterThan(5);
         assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(1000));
         assertThat(chunks).extracting(TextChunker.Chunk::index)
                 .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
     }

     @Test
     void chunk_longText_consecutiveChunksOverlap() {
         var chunks = TextChunker.chunk(List.of(new PageText(1, words(2000))), 1000, 100);

         String start = chunks.get(1).content().substring(0, 20);
         assertThat(chunks.get(0).content()).contains(start);
     }

     @Test
     void chunk_blankPages_returnsNoChunks() {
         var chunks = TextChunker.chunk(List.of(new PageText(1, ""), new PageText(2, "  \n ")), 1000, 100);

         assertThat(chunks).isEmpty();
     }

     @Test
     void chunk_overlapTooLarge_isRejected() {
         assertThatThrownBy(() -> TextChunker.chunk(List.of(), 100, 80))
                 .isInstanceOf(IllegalArgumentException.class);
     }
 }