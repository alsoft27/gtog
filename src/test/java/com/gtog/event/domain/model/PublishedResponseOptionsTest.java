package com.gtog.event.domain.model;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.gtog.event.ResponseTestFixtures.responseEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishedResponseOptionsTest {
	@Test
	void permitsRenamingAnAnsweredOptionWithoutChangingItsIdentity() {
		Event event = published();
		event.replaceResponseOptions(List.of(edit("yes-id", "Confirmo", true), edit("no-id", "No", false)), Set.of("yes-id"));
		assertThat(event.getResponseOptions().getFirst()).isEqualTo(new ResponseOption("yes-id", "Confirmo", true));
	}

	@Test
	void permitsAddingAnOptionWhenOtherOptionsHaveResponses() {
		Event event = published();
		event.replaceResponseOptions(List.of(edit("yes-id", "Asisto", true), edit("no-id", "No", false), edit(null, "Tal vez", false)), Set.of("yes-id"));
		assertThat(event.getResponseOptions()).hasSize(3);
		assertThat(event.getResponseOptions().get(2).id()).isNotBlank().isNotIn("yes-id", "no-id");
	}

	@Test
	void permitsDeletingAnUnansweredOption() {
		Event event = published();
		event.replaceResponseOptions(List.of(edit("yes-id", "Asisto", true), edit(null, "Tal vez", false)), Set.of("yes-id"));
		assertThat(event.getResponseOptions()).extracting(ResponseOption::id).doesNotContain("no-id");
	}

	@Test
	void permitsChangingAttendanceOfAnUnansweredOption() {
		Event event = published();
		event.replaceResponseOptions(List.of(edit("yes-id", "Asisto", true), edit("no-id", "Tambien asisto", true)), Set.of("yes-id"));
		assertThat(event.getResponseOptions().get(1).countsAsAttendance()).isTrue();
	}

	@Test
	void rejectsDeletingAnAnsweredOptionAndPreservesTheOriginalList() {
		Event event = published();
		var original = event.getResponseOptions();
		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(edit("no-id", "No", false)), Set.of("yes-id")))
				.isInstanceOf(ResponseOptionDeletionNotAllowedException.class);
		assertThat(event.getResponseOptions()).isEqualTo(original);
	}

	@ParameterizedTest
	@ValueSource(strings = {"yes-id", "no-id"})
	void rejectsChangingAttendanceOfAnAnsweredOptionInEitherDirection(String answeredId) {
		Event event = published();
		var original = event.getResponseOptions();
		var edits = answeredId.equals("yes-id")
				? List.of(edit("yes-id", "Ahora no", false), edit("no-id", "No", false))
				: List.of(edit("yes-id", "Si", true), edit("no-id", "Ahora si", true));
		assertThatThrownBy(() -> event.replaceResponseOptions(edits, Set.of(answeredId)))
				.isInstanceOf(ResponseOptionAttendanceChangeNotAllowedException.class);
		assertThat(event.getResponseOptions()).isEqualTo(original);
	}

	@Test
	void repeatingAnAnsweredIdCannotBypassTheAttendanceProtection() {
		Event event = published();
		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(edit("yes-id", "Si", true),
				edit("yes-id", "Copia que no cuenta", false)), Set.of("yes-id")))
				.isInstanceOf(ResponseOptionAttendanceChangeNotAllowedException.class);
	}

	@Test
	void draftDoesNotRestrictAnsweredOptions() {
		Event event = responseEvent(EventStatus.DRAFT, true, true, null);
		event.replaceResponseOptions(List.of(edit("yes-id", "Ahora no", false), edit(null, "Ahora si", true)), Set.of("yes-id", "no-id"));
		assertThat(event.getResponseOptions()).extracting(ResponseOption::id).doesNotContain("no-id");
		assertThat(event.getResponseOptions().getFirst().countsAsAttendance()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
	void closedStatesRejectResponseOptionChanges(EventStatus state) {
		Event event = responseEvent(state, true, true, null);
		assertThatThrownBy(() -> event.replaceResponseOptions(List.of(edit("yes-id", "Si", true), edit("no-id", "No", false)), Set.of()))
				.isInstanceOf(EventNotEditableException.class);
	}

	@Test
	void publishedStillRequiresAtLeastTwoOptions() {
		assertThatThrownBy(() -> published().replaceResponseOptions(List.of(edit("yes-id", "Si", true)), Set.of()))
				.isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void publishedStillRejectsMoreThanFiveOptions() {
		assertThatThrownBy(() -> published().replaceResponseOptions(List.of(edit(null, "1", true), edit(null, "2", false),
				edit(null, "3", false), edit(null, "4", false), edit(null, "5", false), edit(null, "6", false)), Set.of()))
				.isInstanceOf(InvalidResponseOptionCountException.class);
	}

	@Test
	void publishedStillRequiresAnAttendanceOption() {
		assertThatThrownBy(() -> published().replaceResponseOptions(List.of(edit("yes-id", "1", false), edit("no-id", "2", false)), Set.of()))
				.isInstanceOf(NoAttendanceResponseOptionException.class);
	}

	@Test
	void publishedStillRequiresNonBlankLabels() {
		assertThatThrownBy(() -> published().replaceResponseOptions(List.of(edit("yes-id", " ", true), edit("no-id", "No", false)), Set.of()))
				.isInstanceOf(BlankResponseOptionLabelException.class);
	}

	@Test
	void publishedStillRequiresUniqueLabels() {
		assertThatThrownBy(() -> published().replaceResponseOptions(List.of(edit("yes-id", "Si", true), edit("no-id", "Si", false)), Set.of()))
				.isInstanceOf(DuplicateResponseOptionLabelException.class);
	}

	private Event published() { return responseEvent(EventStatus.PUBLISHED, true, true, null); }
	private ResponseOptionEdit edit(String id, String label, boolean attendance) { return new ResponseOptionEdit(id, label, attendance); }
}
