"""Build strict prompts for OpenAI wireframe generation."""
from typing import Dict, List


SYSTEM_MESSAGE = """You are a wireframe generator that converts GitHub issue data into a single, well-structured wireframe Markdown document. Follow all instructions exactly and do not deviate. Use the issue inputs only to produce the wireframe requested in the user message. If you cannot follow the output rules, return exactly: ERROR: CANNOT PRODUCE WIREFRAME"""


USER_MESSAGE_TEMPLATE = """INPUT DATA (will be injected here):  
- Issue title: {issue_title}
- Issue body: {issue_body}
- Comments (chronological): {comments}
- Labels: {labels}

FILENAME FORMAT: The target filename will be {shortname}-{issue_number}.wf.md where `shortname` is a slugified version of the issue title and `issue_number` is the GitHub issue number.

REQUIRED OUTPUT: Produce ONE Markdown wireframe document and NOTHING ELSE. The document must contain the following sections in this exact order, each as a top-level Markdown heading (use `#` for Title and `##` for other sections):

# Title
## Purpose
## Components
## Layout
## Interaction Flow
## Notes

CONTENT GUIDANCE:
- Title: use the issue title verbatim.
- Purpose: synthesize the issue body into 2–4 concise sentences stating the goal and user problem.
- Components: list UI components as short bullets (e.g., header, form fields, buttons, lists, modals) inferred from issue + comments.
- Layout: describe hierarchy and placement (top, left, main, right, footer) with short bullets or a simple ASCII-style layout sketch (no fenced code blocks).
 - Layout: describe hierarchy and placement (top, left, main, right, footer) with short bullets or brief inline ASCII layout hints (no fenced blocks, 1–3 lines max).
- Interaction Flow: give step-by-step numbered bullets for primary user flows and key edge cases referenced in comments.
- Notes: include constraints, acceptance criteria, important comments, and TODOs for designers/developers. Use labels to influence tone/context but DO NOT print labels.

STRICT OUTPUT RULE (MUST BE FOLLOWED):
OUTPUT ONLY THE WIREFRAME. NO EXPLANATION, NO PREAMBLE, NO FOOTER, NO METADATA, NO JSON, NO CODE BLOCKS, NO MARKDOWN FENCED BLOCKS, NO ADDITIONAL TEXT OR CHATTER. If you cannot comply with this exact rule, output exactly:
ERROR: CANNOT PRODUCE WIREFRAME

ADDITIONAL RULES:
- BEGIN OUTPUT EXACTLY WITH: `# {issue_title}`
- After the H1 title, include the sections in the exact order shown above. Do not add any text before or between headings.
- Use bullet lists for Components and Layout; use numbered steps for Interaction Flow.
- Keep the wireframe concise and human-readable (roughly 100–800 words).
- Use plain Markdown headings and lists only.
- Do not include the filename, file path, token counts, or any system/debug info in the output.
- Do not ask questions — produce the wireframe from the provided inputs.

Now generate the wireframe using the provided inputs."""


class PromptBuilder:
    """Build prompts for OpenAI API."""
    
    def __init__(self, summarizer=None):
        """
        Initialize prompt builder.
        
        Args:
            summarizer: Optional TextSummarizer instance for long threads
        """
        self.summarizer = summarizer
        
    def build_prompt(
        self,
        issue: Dict,
        comments: List[Dict],
        labels: List[str]
    ) -> Dict:
        """
        Build OpenAI API messages.
        
        Args:
            issue: Issue dictionary
            comments: List of comment dictionaries
            labels: List of label names
            
        Returns:
            Dictionary with 'messages' list for OpenAI API
        """
        issue_title = issue.get('title', '')
        issue_number = issue.get('number', 0)
        
        # Get issue body - use summary if available and thread is long
        issue_body = issue.get('body', '') or ''
        
        # If summarizer is provided, use it for long threads
        if self.summarizer and (len(issue_body) + self._total_comment_length(comments)) > 2000:
            issue_body = self.summarizer.summarize_locally(issue, comments)
            comments_text = "(Summarized above)"
        else:
            # Format comments chronologically
            comments_text = self._format_comments(comments)
            
        # Format labels
        labels_text = ', '.join(labels) if labels else 'none'
        
        # Build user message from template
        user_message = USER_MESSAGE_TEMPLATE.format(
            issue_title=issue_title,
            issue_body=issue_body,
            comments=comments_text,
            labels=labels_text,
            issue_number=issue_number,
            shortname='(auto-generated)'
        )
        
        return {
            'messages': [
                {'role': 'system', 'content': SYSTEM_MESSAGE},
                {'role': 'user', 'content': user_message}
            ]
        }
        
    def _format_comments(self, comments: List[Dict]) -> str:
        """Format comments for prompt."""
        if not comments:
            return "No comments"
            
        # Sort by creation time
        sorted_comments = sorted(comments, key=lambda c: c.get('created_at', ''))
        
        formatted = []
        for i, comment in enumerate(sorted_comments[:10], 1):  # Limit to 10 comments
            author = comment.get('user', {}).get('login', 'unknown')
            date = comment.get('created_at', '')[:10]  # Just date part
            body = (comment.get('body', '') or '').strip()
            
            # Truncate very long comments
            if len(body) > 500:
                body = body[:497] + '...'
                
            formatted.append(f"{i}. {author} ({date}): {body}")
            
        if len(comments) > 10:
            formatted.append(f"... and {len(comments) - 10} more comments")
            
        return '\n'.join(formatted)
        
    def _total_comment_length(self, comments: List[Dict]) -> int:
        """Calculate total length of all comments."""
        total = 0
        for comment in comments:
            body = comment.get('body', '') or ''
            total += len(body)
        return total
        
    def validate_summary(self, summary: str) -> bool:
        """
        Validate that summary is under size limits.
        
        Args:
            summary: Summary text
            
        Returns:
            True if valid
        """
        return len(summary) <= 2000 and len(summary.strip()) > 0
