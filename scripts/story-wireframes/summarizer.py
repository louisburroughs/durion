"""Local TextRank summarization with map-reduce for long threads."""
import re
from typing import List, Dict
from sumy.parsers.plaintext import PlaintextParser
from sumy.nlp.tokenizers import Tokenizer
from sumy.summarizers.text_rank import TextRankSummarizer
from sumy.nlp.stemmers import Stemmer
from sumy.utils import get_stop_words
import nltk

# Download required NLTK data
try:
    nltk.data.find('tokenizers/punkt')
except LookupError:
    nltk.download('punkt', quiet=True)


class TextSummarizer:
    """TextRank-based summarizer with map-reduce support."""
    
    def __init__(
        self,
        chunk_max_chars: int = 3500,
        overlap_chars: int = 200,
        map_sentences: int = 3,
        reduce_sentences: int = 6,
        max_total_chars: int = 2000
    ):
        """
        Initialize summarizer.
        
        Args:
            chunk_max_chars: Maximum characters per chunk
            overlap_chars: Overlap between chunks
            map_sentences: Sentences per chunk summary
            reduce_sentences: Sentences in final summary
            max_total_chars: Maximum total characters in output
        """
        self.chunk_max_chars = chunk_max_chars
        self.overlap_chars = overlap_chars
        self.map_sentences = map_sentences
        self.reduce_sentences = reduce_sentences
        self.max_total_chars = max_total_chars
        
        # Initialize TextRank summarizer
        self.stemmer = Stemmer('english')
        self.summarizer = TextRankSummarizer(self.stemmer)
        self.summarizer.stop_words = get_stop_words('english')
        
    def preprocess_texts(
        self,
        title: str,
        body: str,
        comments: List[Dict]
    ) -> List[str]:
        """
        Preprocess and extract sentences chronologically.
        
        Args:
            title: Issue title
            body: Issue body
            comments: List of comment dicts with 'body' and 'created_at'
            
        Returns:
            List of cleaned sentences in chronological order
        """
        # Combine all text chronologically
        texts = [title, body or '']
        
        # Sort comments by creation time and add their bodies
        sorted_comments = sorted(comments, key=lambda c: c.get('created_at', ''))
        for comment in sorted_comments:
            comment_body = comment.get('body', '')
            if comment_body:
                texts.append(comment_body)
                
        # Join and clean
        full_text = ' '.join(texts)
        
        # Remove code blocks
        full_text = re.sub(r'```[\s\S]*?```', '', full_text)
        full_text = re.sub(r'`[^`]+`', '', full_text)
        
        # Remove URLs
        full_text = re.sub(r'https?://\S+', '', full_text)
        
        # Remove quoted text (lines starting with >)
        lines = full_text.split('\n')
        lines = [line for line in lines if not line.strip().startswith('>')]
        full_text = '\n'.join(lines)
        
        # Remove control characters
        full_text = re.sub(r'[\x00-\x08\x0b-\x0c\x0e-\x1f\x7f]', '', full_text)
        
        # Split into sentences
        tokenizer = Tokenizer('english')
        sentences = []
        for sent in nltk.sent_tokenize(full_text):
            sent = sent.strip()
            if sent and len(sent) > 10:  # Skip very short sentences
                sentences.append(sent)
                
        return sentences
        
    def chunk_texts(
        self,
        sentences: List[str],
        max_chars: int = None,
        overlap_chars: int = None
    ) -> List[str]:
        """
        Chunk sentences with overlap.
        
        Args:
            sentences: List of sentences
            max_chars: Maximum characters per chunk (uses default if None)
            overlap_chars: Overlap between chunks (uses default if None)
            
        Returns:
            List of text chunks
        """
        max_chars = max_chars or self.chunk_max_chars
        overlap_chars = overlap_chars or self.overlap_chars
        
        chunks = []
        current_chunk = []
        current_length = 0
        overlap_sentences = []
        
        for sentence in sentences:
            sent_length = len(sentence)
            
            # If adding this sentence exceeds limit, save chunk
            if current_length + sent_length > max_chars and current_chunk:
                chunks.append(' '.join(current_chunk))
                
                # Start next chunk with overlap
                overlap_length = 0
                overlap_sentences = []
                
                # Add sentences from end of current chunk for overlap
                for s in reversed(current_chunk):
                    if overlap_length + len(s) <= overlap_chars:
                        overlap_sentences.insert(0, s)
                        overlap_length += len(s)
                    else:
                        break
                        
                current_chunk = overlap_sentences.copy()
                current_length = overlap_length
                
            current_chunk.append(sentence)
            current_length += sent_length
            
        # Add final chunk
        if current_chunk:
            chunks.append(' '.join(current_chunk))
            
        return chunks
        
    def summarize_chunk(self, chunk_text: str, sentences: int) -> str:
        """
        Summarize a single chunk using TextRank.
        
        Args:
            chunk_text: Text to summarize
            sentences: Number of sentences to extract
            
        Returns:
            Summary text
        """
        if not chunk_text or not chunk_text.strip():
            return ""
            
        parser = PlaintextParser.from_string(chunk_text, Tokenizer('english'))
        summary_sentences = self.summarizer(parser.document, sentences)
        
        # Convert to strings and join
        summary = ' '.join([str(sent) for sent in summary_sentences])
        return summary
        
    def map_reduce_summarize(
        self,
        title: str,
        body: str,
        comments: List[Dict]
    ) -> str:
        """
        Summarize using map-reduce approach.
        
        Args:
            title: Issue title
            body: Issue body
            comments: List of comment dicts
            
        Returns:
            Final summary text
        """
        # Preprocess to get sentences
        sentences = self.preprocess_texts(title, body, comments)
        
        if not sentences:
            return title
            
        # Join to check total length
        full_text = ' '.join(sentences)
        
        # Short thread: single summarization pass
        if len(full_text) <= self.chunk_max_chars:
            return self.summarize_chunk(full_text, self.reduce_sentences)
            
        # Long thread: map-reduce
        chunks = self.chunk_texts(sentences)
        
        # Map: summarize each chunk
        map_summaries = []
        for chunk in chunks:
            summary = self.summarize_chunk(chunk, self.map_sentences)
            if summary:
                map_summaries.append(summary)
                
        # Reduce: summarize the summaries
        combined = ' '.join(map_summaries)
        reduced = self.summarize_chunk(combined, self.reduce_sentences)
        
        # Second reduce if still too long
        if len(reduced) > self.max_total_chars:
            reduced = self.summarize_chunk(
                reduced,
                max(1, self.reduce_sentences // 2)
            )
            
        return reduced.strip()
        
    def summarize_locally(
        self,
        issue: Dict,
        comments: List[Dict]
    ) -> str:
        """
        Public entrypoint for summarization.
        
        Args:
            issue: Issue dictionary with 'title' and 'body'
            comments: List of comment dictionaries
            
        Returns:
            Summary text
        """
        title = issue.get('title', '')
        body = issue.get('body', '')
        
        return self.map_reduce_summarize(title, body, comments)
