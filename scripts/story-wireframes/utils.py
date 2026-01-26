"""Utilities for wireframe generation."""
import time
import random
import re
from typing import Any, Callable, Dict, List
from slugify import slugify


def retry_with_backoff(
    callable_func: Callable,
    max_attempts: int = 5,
    base_delay: float = 1.0,
    max_delay: float = 60.0,
    jitter: bool = True
) -> Any:
    """
    Retry a callable with exponential backoff.
    
    Args:
        callable_func: Function to retry
        max_attempts: Maximum number of retry attempts
        base_delay: Base delay in seconds
        max_delay: Maximum delay in seconds
        jitter: Add random jitter to delay
        
    Returns:
        Result of callable_func
        
    Raises:
        Exception: After max_attempts failures
    """
    attempt = 1
    
    while attempt <= max_attempts:
        try:
            return callable_func()
        except Exception as e:
            # Check if it's a retryable error
            if not _is_retryable_error(e):
                raise
                
            if attempt == max_attempts:
                raise
                
            # Compute backoff delay
            delay = min(max_delay, base_delay * (2 ** (attempt - 1)))
            
            # Add jitter if requested
            if jitter:
                delay += random.uniform(0, base_delay)
                
            # Check for Retry-After header if it's an HTTP error
            retry_after = _get_retry_after(e)
            if retry_after:
                delay = min(max_delay, retry_after)
                
            print(f"Attempt {attempt}/{max_attempts} failed: {e}. Retrying in {delay:.2f}s...")
            time.sleep(delay)
            attempt += 1


def _is_retryable_error(error: Exception) -> bool:
    """Check if error is retryable."""
    error_str = str(error).lower()
    
    # HTTP status codes that should trigger retry
    retryable_statuses = ['429', '502', '503', '504']
    
    # Check for retryable HTTP statuses
    for status in retryable_statuses:
        if status in error_str:
            return True
            
    # Check for network/timeout errors
    retryable_patterns = ['timeout', 'connection', 'network']
    for pattern in retryable_patterns:
        if pattern in error_str:
            return True
            
    return False


def _get_retry_after(error: Exception) -> float:
    """Extract Retry-After value from error if present."""
    # This is a simplified version - in real implementation,
    # you'd parse the actual HTTP response headers
    error_str = str(error)
    match = re.search(r'retry[_\s-]after[:\s]+(\d+)', error_str, re.IGNORECASE)
    if match:
        return float(match.group(1))
    return None


def slugify_name(name: str, max_length: int = 50) -> str:
    """
    Convert name to URL-friendly slug.
    
    Args:
        name: Name to slugify
        max_length: Maximum length of slug
        
    Returns:
        Slugified name
    """
    slug = slugify(name, lowercase=True, max_length=max_length)
    # Collapse multiple hyphens
    slug = re.sub(r'-+', '-', slug)
    # Remove leading/trailing hyphens
    slug = slug.strip('-')
    return slug


def sanitize_markdown(md: str) -> str:
    """
    Sanitize markdown content.
    
    Args:
        md: Markdown content
        
    Returns:
        Sanitized markdown
    """
    # Remove control characters except newlines and tabs
    md = re.sub(r'[\x00-\x08\x0b-\x0c\x0e-\x1f\x7f]', '', md)
    
    # Normalize line endings
    md = md.replace('\r\n', '\n').replace('\r', '\n')
    
    # Trim trailing spaces from lines
    lines = [line.rstrip() for line in md.split('\n')]
    md = '\n'.join(lines)
    
    # Collapse excessive blank lines (max 2 consecutive)
    md = re.sub(r'\n{3,}', '\n\n', md)
    
    # Ensure single trailing newline
    md = md.rstrip() + '\n'
    
    return md


def map_labels_to_domain(labels: List[str], mapping: Dict[str, str]) -> str:
    """
    Map issue labels to domain.
    
    Args:
        labels: List of issue labels
        mapping: Label to domain mapping
        
    Returns:
        Domain name
    """
    def _normalize(label: str) -> str:
        """Lowercase and strip non-alphanumerics for fuzzy matching."""
        return re.sub(r'[^a-z0-9]+', '', label.lower())

    # Precompute normalized mapping (excluding default)
    normalized_mapping = {}
    for key, domain in mapping.items():
        if key == 'default':
            continue
        normalized_key = _normalize(key)
        if normalized_key:
            normalized_mapping[normalized_key] = domain

    # Exact match (normalized)
    for label in labels:
        norm = _normalize(label)
        if norm in normalized_mapping:
            return normalized_mapping[norm]

    # Partial/fuzzy match (normalized contains)
    for label in labels:
        norm = _normalize(label)
        for norm_key, domain in normalized_mapping.items():
            if norm_key and norm_key in norm:
                return domain

    return mapping.get('default', 'general')
