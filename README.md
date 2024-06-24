Phobrain is a toy or tool for examining how one's mind works, starting by doing 'compare and contrast' on pairs of photos - to cultivate multidimensional thinking and curiosity. Labeling pairs like/not enables training simple neural nets that can project unseen pairs. Weighing pairs leads to clashes in rationales, introducing classification in a nitty-gritty, personal way.

The setup should become more user-friendly, starting from this programmer-level description:

    Copy photos to an images folder. 
    Color information and Imagenet vectors are extracted. 
    Create postgres database, with pgvector. 
    Import photo info into database. 
    Compile/start web server. 
    View/label photo pairs via browser. 
    Train models, predict pairs, view.

The (Java) web server is intended for local use as a default for data protection. It is old school, and I hope to see AGPL-licensed rewrites in new languages. Much data prep code is also in Java, which is used for its fantastic parallelism compared to python. Training/prediction use keras; much optimization could be done for batch predictions.

Neural nets: color histograms and Imagenet vectors are used to represent photos. Pairs labeled 0/1 train nets to ~85% accuracy. Then vectors of size 2..16 are generated for the photos using those nets (these are personal pair analogs of Imagenet vectors), and groups of these smaller vectors are again taken to represent the photos, using the original labeled pairs to train miniscule nets that predict interesting photos with ~90% accuracy.

Observation: the gold standard for labeling is to use randomly-generated pairs. Alternative methods are provided for variety.


