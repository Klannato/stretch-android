Bundled stretch footage lives here.

One file per stretch, named after its id from stretches.json:

    side-bend.mp4
    side-bend_2.mp4        <- second camera angle for the same stretch
    quad.mov               <- not read: must be .mp4

Add clips with tools/add_footage.sh, which transcodes to H.264/MP4, strips audio and
caps the height:

    tools/add_footage.sh quad ~/Videos/quad-front.mov 1

Any stretch with a clip here plays it in the session instead of the drawn figure and
the generated illustration. Stretches without one keep the drawings as a fallback.
