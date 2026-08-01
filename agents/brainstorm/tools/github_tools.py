"""
GitHub API tools for the Feature Brainstorm Agent.

Provides tools to interact with GitHub Issues, Labels, Milestones,
and GitHub Projects v2 (GraphQL API).
"""

import os
import json
import urllib.request
import urllib.error
from typing import Optional


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _github_token() -> str:
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        raise EnvironmentError(
            "GITHUB_TOKEN environment variable is not set. "
            "Please set it in .agents/agents/brainstorm/.env"
        )
    return token


def _rest_request(
    method: str,
    path: str,
    payload: Optional[dict] = None,
) -> dict:
    """Make an authenticated GitHub REST API request."""
    url = f"https://api.github.com{path}"
    token = _github_token()
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
        "User-Agent": "EventVerse-BrainstormAgent/1.0",
    }
    data = json.dumps(payload).encode() if payload else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        raise RuntimeError(f"GitHub API error {e.code}: {body}") from e


def _graphql_request(query: str, variables: dict) -> dict:
    """Make an authenticated GitHub GraphQL API request."""
    url = "https://api.github.com/graphql"
    token = _github_token()
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "User-Agent": "EventVerse-BrainstormAgent/1.0",
    }
    payload = json.dumps({"query": query, "variables": variables}).encode()
    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read().decode())
            if "errors" in result:
                raise RuntimeError(f"GraphQL errors: {result['errors']}")
            return result
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        raise RuntimeError(f"GitHub GraphQL error {e.code}: {body}") from e


# ---------------------------------------------------------------------------
# Tools
# ---------------------------------------------------------------------------

def list_github_labels(repo: str) -> str:
    """Lists all labels available in the given GitHub repository.

    Args:
        repo: The repository in 'owner/repo' format, e.g. 'amalari/event-verse'.

    Returns:
        A formatted string listing all label names and their descriptions.
    """
    labels = _rest_request("GET", f"/repos/{repo}/labels?per_page=100")
    if not labels:
        return "No labels found in the repository."
    lines = ["Available labels:"]
    for lbl in labels:
        desc = f" — {lbl['description']}" if lbl.get("description") else ""
        lines.append(f"  • {lbl['name']}{desc}")
    return "\n".join(lines)


def list_github_milestones(repo: str) -> str:
    """Lists all open milestones in the given GitHub repository.

    Args:
        repo: The repository in 'owner/repo' format, e.g. 'amalari/event-verse'.

    Returns:
        A formatted string listing all open milestone titles and due dates.
    """
    milestones = _rest_request("GET", f"/repos/{repo}/milestones?state=open&per_page=50")
    if not milestones:
        return "No open milestones found."
    lines = ["Open milestones:"]
    for ms in milestones:
        due = ms.get("due_on", "no due date")
        if due and due != "no due date":
            due = due[:10]  # ISO date only
        lines.append(f"  • [{ms['number']}] {ms['title']} (due: {due})")
    return "\n".join(lines)


def create_github_issue(
    repo: str,
    title: str,
    body: str,
    labels: list[str],
    milestone_number: Optional[int] = None,
    assignees: Optional[list[str]] = None,
) -> str:
    """Creates a new GitHub Issue in the given repository.

    Args:
        repo: The repository in 'owner/repo' format, e.g. 'amalari/event-verse'.
        title: The issue title.
        body: The issue body (Markdown). Should follow the EventVerse issue template.
        labels: List of label names to apply to the issue.
        milestone_number: The milestone number (integer) to assign. Optional.
        assignees: List of GitHub usernames to assign. Optional.

    Returns:
        A summary string with the issue number, title, and URL.
    """
    payload: dict = {"title": title, "body": body, "labels": labels}
    if milestone_number is not None:
        payload["milestone"] = milestone_number
    if assignees:
        payload["assignees"] = assignees

    result = _rest_request("POST", f"/repos/{repo}/issues", payload)
    number = result["number"]
    url = result["html_url"]
    return f"✅ Issue #{number} created successfully!\nTitle: {title}\nURL: {url}"


def add_to_github_project(issue_node_id: str, project_number: int, owner: str) -> str:
    """Adds a GitHub Issue to a GitHub Project (v2) board.

    Args:
        issue_node_id: The GraphQL node ID of the issue (from create_github_issue response).
        project_number: The GitHub Project number (integer), e.g. 1.
        owner: The owner (user or org) of the project, e.g. 'amalari'.

    Returns:
        A confirmation string if the item was added successfully.
    """
    # 1. Fetch the project's node ID
    query_project = """
    query($owner: String!, $number: Int!) {
      user(login: $owner) {
        projectV2(number: $number) {
          id
          title
        }
      }
    }
    """
    result = _graphql_request(query_project, {"owner": owner, "number": project_number})
    project_data = result.get("data", {}).get("user", {}).get("projectV2")
    if not project_data:
        # Try org
        query_org = """
        query($owner: String!, $number: Int!) {
          organization(login: $owner) {
            projectV2(number: $number) {
              id
              title
            }
          }
        }
        """
        result = _graphql_request(query_org, {"owner": owner, "number": project_number})
        project_data = result.get("data", {}).get("organization", {}).get("projectV2")
    if not project_data:
        return f"❌ Could not find Project #{project_number} for owner '{owner}'."

    project_id = project_data["id"]
    project_title = project_data["title"]

    # 2. Add the issue to the project
    mutation = """
    mutation($projectId: ID!, $contentId: ID!) {
      addProjectV2ItemById(input: {projectId: $projectId, contentId: $contentId}) {
        item {
          id
        }
      }
    }
    """
    _graphql_request(mutation, {"projectId": project_id, "contentId": issue_node_id})
    return f"✅ Issue added to project '{project_title}' (#{project_number}) successfully!"


def get_issue_node_id(repo: str, issue_number: int) -> str:
    """Fetches the GraphQL node ID of a GitHub Issue.

    Needed to add an issue to a GitHub Project v2.

    Args:
        repo: The repository in 'owner/repo' format.
        issue_number: The issue number.

    Returns:
        The GraphQL node ID string of the issue.
    """
    result = _rest_request("GET", f"/repos/{repo}/issues/{issue_number}")
    return result.get("node_id", "")
