"""
Compatibility graph entry for Coze runtime.

This project exports an agent implementation under `agents.agent`.
Some runtimes call `graphs.graph` in workflow mode, so we expose a
compiled graph object here by reusing `build_agent()`.
"""

from agents.agent import build_agent

# `coze_coding_utils.helper.graph_helper.get_graph_instance` scans module
# members and picks the first CompiledStateGraph object.
graph = build_agent()

