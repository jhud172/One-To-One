package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import java.util.List;

public record TrainerLibraryAssignedExerciseView(TrainerLibraryExercise exercise,
                                                 List<TrainerLibraryExerciseNote> notes, String videoUrl) { }
