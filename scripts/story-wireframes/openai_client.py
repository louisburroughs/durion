"""OpenAI API client with retry logic."""
import os
from typing import Dict
from openai import OpenAI
from utils import retry_with_backoff


class OpenAIClient:
    """Client for OpenAI API with retry logic."""
    
    def __init__(
        self,
        api_key: str = None,
        model: str = "gpt-4",
        temperature: float = 0.0,
        max_completion_tokens: int = 900,
        max_tokens: int = None  # legacy support; mapped to max_completion_tokens
    ):
        """
        Initialize OpenAI client.
        
        Args:
            api_key: OpenAI API key (defaults to OPENAI_API_KEY env var)
            model: Model to use
            temperature: Temperature parameter
            max_completion_tokens: Maximum completion tokens to generate
            max_tokens: Legacy parameter (mapped to max_completion_tokens)
        """
        api_key = api_key or os.getenv('OPENAI_API_KEY')
        if not api_key:
            raise ValueError("OpenAI API key required. Set OPENAI_API_KEY environment variable.")
            
        self.client = OpenAI(api_key=api_key)
        self.model = model
        self.temperature = temperature
        # Support legacy max_tokens by mapping to the new param
        if max_tokens and not max_completion_tokens:
            max_completion_tokens = max_tokens
        self.max_completion_tokens = max_completion_tokens
        
    def call_openai(
        self,
        messages: list,
        model: str = None,
        temperature: float = None,
        max_completion_tokens: int = None,
        max_tokens: int = None  # legacy
    ) -> str:
        """
        Call OpenAI API with retry logic.
        
        Args:
            messages: List of message dicts
            model: Override default model
            temperature: Override default temperature
            max_completion_tokens: Override default max completion tokens
            max_tokens: Legacy override (mapped)
            
        Returns:
            Generated text
        """
        model = model or self.model
        temperature = temperature if temperature is not None else self.temperature
        # Map legacy max_tokens to the new parameter name
        if max_completion_tokens is None:
            max_completion_tokens = max_tokens
        if max_completion_tokens is None:
            max_completion_tokens = self.max_completion_tokens
        
        def _call():
            response = self.client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=temperature,
                max_completion_tokens=max_completion_tokens
            )
            
            return response.choices[0].message.content.strip()
            
        # Use retry wrapper
        return retry_with_backoff(_call)
        
    def validate_wireframe(self, wireframe: str) -> bool:
        """
        Validate wireframe output.
        
        Args:
            wireframe: Generated wireframe text
            
        Returns:
            True if valid, False otherwise
        """
        import re

        # Basic error guard
        if not wireframe or wireframe.strip() == "ERROR: CANNOT PRODUCE WIREFRAME":
            return False

        lines = [line.rstrip() for line in wireframe.splitlines()]
        non_empty = [line for line in lines if line.strip()]

        # First non-empty line must be an H1 title
        if not non_empty or not re.match(r'^#\s+.+', non_empty[0]):
            return False

        # Required sections in order
        required_patterns = [
            ("title", re.compile(r'^#\s+.+')),
            ("purpose", re.compile(r'^##\s+Purpose')),
            ("components", re.compile(r'^##\s+Components')),
            ("layout", re.compile(r'^##\s+Layout')),
            ("interaction", re.compile(r'^##\s+Interaction Flow')),
            ("notes", re.compile(r'^##\s+Notes')),
        ]

        section_indices = {}
        search_start = 0
        for name, pattern in required_patterns:
            found_index = None
            for idx in range(search_start, len(non_empty)):
                if pattern.match(non_empty[idx]):
                    found_index = idx
                    break
            if found_index is None:
                return False
            section_indices[name] = found_index
            search_start = found_index + 1

        # Ensure order is strictly ascending
        ordered_indices = [section_indices[name] for name, _ in required_patterns]
        if ordered_indices != sorted(ordered_indices):
            return False

        def section_block(start_name: str, end_name: str = None) -> list:
            start = section_indices[start_name] + 1
            end = section_indices[end_name] if end_name else len(non_empty)
            return [line for line in non_empty[start:end] if line.strip()]

        components_block = section_block("components", "layout")
        layout_block = section_block("layout", "interaction")
        interaction_block = section_block("interaction", "notes")

        # Components and layout should contain bullet-like lines
        has_component_bullets = any(line.lstrip().startswith(('-', '*')) for line in components_block)
        has_layout_bullets = any(
            line.lstrip().startswith(('-', '*')) or re.match(r'^[A-Za-z]+:', line.strip())
            for line in layout_block
        )
        # Interaction flow should contain numbered steps
        has_numbered_steps = any(re.match(r'^\d+[\.)]\s', line.strip()) for line in interaction_block)

        return has_component_bullets and has_layout_bullets and has_numbered_steps
