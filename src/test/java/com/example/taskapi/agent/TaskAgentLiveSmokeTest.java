package com.example.taskapi.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.taskapi.task.Task;
import com.example.taskapi.task.TaskService;

/**
 * Optional smoke test against the REAL Anthropic API. It makes one paid request
 * (a fraction of a cent on Claude Haiku 5.5).
 *
 * <p>Skipped unless BOTH environment variables are set, so having an API key in your
 * environment never causes a paid call during a normal build:
 * <pre>
 * ANTHROPIC_API_KEY=...  TASK_AGENT_LIVE_TEST=true  ./mvnw test -Dtest=TaskAgentLiveSmokeTest
 * </pre>
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "TASK_AGENT_LIVE_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SpringBootTest
class TaskAgentLiveSmokeTest {

	@Autowired
	private TaskAgentService agentService;

	@Autowired
	private TaskService taskService;

	@Test
	void realModelCreatesTaskViaToolCall() {
		AgentChatResponse response = agentService.chat(
				"Create a task titled 'Live smoke test' with the description 'Created by the live test'.");

		assertThat(response.actions())
				.as("the model should have called create_task exactly once")
				.singleElement()
				.satisfies(action -> {
					assertThat(action.type()).isEqualTo(AgentAction.Type.CREATED);
					assertThat(action.title()).isEqualToIgnoringCase("Live smoke test");
				});
		assertThat(taskService.findAll()).extracting(Task::title)
				.anySatisfy(title -> assertThat(title).isEqualToIgnoringCase("Live smoke test"));
		assertThat(response.reply()).isNotBlank();
	}
}
