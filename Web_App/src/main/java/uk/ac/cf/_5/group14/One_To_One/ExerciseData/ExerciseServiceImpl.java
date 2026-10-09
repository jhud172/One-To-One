package uk.ac.cf._5.group14.One_To_One.ExerciseData;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.ConditionsPreferences.UserPreference.UserPreferenceRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class ExerciseServiceImpl implements  ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ExerciseRepository repo;

    @Override
    public List<Exercise> getAllExercises() {
        List<Exercise> list = new ArrayList<>();
        repo.findAll().forEach(list::add);
        return list;
    }

    @Override
    public Exercise getExerciseById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    public void saveExercise(Exercise exercise) {
        repo.save(exercise);
    }


    @Override
    public List<Exercise> suggestExercises(User user) {

        Set<Tag> preferredTags = userPreferenceRepository.getPreferredTags(user);
        Set<Tag> bannedTags = userPreferenceRepository.getBannedTags(user);
        if (preferredTags.isEmpty()) {
            return bannedTags.isEmpty() ? exerciseRepository.findAll() : exerciseRepository.getExercisesExcludingTags(bannedTags);
        }
        // Method for retrieving a list of exercises based on the tags the user chose
        List<Exercise> suggestions = exerciseRepository.getFilteredSuggestedExercises(preferredTags, bannedTags);

        return suggestions;
    }
}
