package net.kuafuai.andee.ui.ball

/** Geometry and materials for a companion; all looks share the full mood and motion engine. */
class BallLook(
    /** For log lines, and for whoever is reading the diff. */
    val name: String,

    // ---- Body material ----
    /** The fill with no mood mixed in. */
    val bodyBase: FloatArray,

    /** How much of [EmotionState.nowColor] the fill takes on. */
    val bodyTint: Float,
    val bodyAlbedo: Float,

    /** How far the vertical gradient darkens the underside. */
    val bodyGrad: Float,
    val bodyAmb: Float,
    val bodyDiff: Float,

    /**
     * Specular weight. Far below 1 on purpose: at 0.95 the highlight is a
     * blown-out white blob a third of the ball wide, sitting on top of the face.
     */
    val bodyGloss: Float,

    /**
     * Radius of the core sphere. Must stay below `RIM` in
     * [EmotionBallRenderer] — see the class KDoc there — and the gap between
     * the two is what the dark outline is drawn in when [rimDark].
     */
    val bodyScale: Float,

    // ---- Face geometry ----
    val faceZ: Float,
    val eyeX: Float,
    val eyeY: Float,
    val eyeRadius: Float,

    /** The eye's cylinder section; [eyeRadius] alone would make it a ball. */
    val eyeCyl: Float,

    val mouthR: Float,
    val mouthTube: Float,
    val mouthY: Float,

    /** Constant thickness added to the mouth, so a resting smile is not a hairline. */
    val mouthThick: Float,

    // ---- Face material ----
    val eyeColor: FloatArray,

    /**
     * Radius of the dark disc inside each eye, or 0 for a solid eye.
     *
     * Jelly wants one: white eyes are the character, and a pupil is what stops
     * them reading as a slot. Cream does not — on a pale ball a white eye has
     * nothing to contrast against, so its eye is the dark disc and [glintR]
     * carries the life instead.
     */
    val pupilR: Float,

    /** The white speck that makes an eye look wet. 0 for none. */
    val glintR: Float,

    /** Draw the mouth as a deep shade of the mood rather than the mood colour. */
    val mouthDeep: Boolean,

    // ---- Snot bubble ----
    //
    // Geometry lives here rather than in the mood table for the same reason the
    // eyes do: the renderer draws the bubble against the same face plane, and
    // where it sits relative to the eyes and mouth is a property of the face,
    // not of any one mood. *Whether* there is a bubble is the mood's business
    // ([Face.bubble]).
    val bubbleX: Float,
    val bubbleY: Float,

    /** Radius of the bubble at full inflation. It pulses between 0.3x and 1x. */
    val bubbleR: Float,

    /**
     * 水滴的颜色：轮廓那一圈，以及内部那层薄雾。
     *
     * 按 look 给，因为它决定的是"边比球体暗还是亮"。奶油球是浅色，水滴的边
     * 读作**折射**（暗）；果冻球本身就是近黑，暗边没有对手，只能反过来用亮边。
     */
    val bubbleColor: FloatArray,

    /** 边缘收紧程度。小 = 一圈宽晕，大 = 一条细线。 */
    val bubblePow: Float,

    /** 高光点的收紧程度。 */
    val bubbleShine: Float,

    /**
     * 内部那层很薄的雾，0 = 完全透明。
     *
     * 故意留一点点而不是 0：一个只有轮廓的水滴在缩略图尺寸下会读成一个圆环，
     * 而带一点厚度才读成"一颗"。0.10 大约等于球体亮度被压掉 1.7%。
     */
    val bubbleInner: Float,

    // ---- Cheeks ----
    val blushColor: FloatArray,
    val blushAlpha: Float,

    /**
     * Blend the cheeks normally instead of adding them.
     *
     * Additive over a near-black body is a warm patch; additive over a pale one
     * pushes the cheek to clipped white and the warmth disappears. Pale looks
     * want normal blending with a saturated pink.
     */
    val blushBlendNormal: Boolean,
    val blushSize: Float,
    val blushX: Float,
    val blushY: Float,

    // ---- Outline ----
    /**
     * Draw a tight dark edge instead of an additive glow.
     *
     * A pale ball has nothing to hold it apart from a pale app behind it, and
     * the glow is colour-additive so it does not survive a white background
     * either. The dark edge does, and it is what makes the ball read as a
     * sticker rather than as something illuminated.
     */
    val rimDark: Boolean,
    val rimGain: Float,

    /** Fresnel exponent: small is a soft halo, large is a crisp contour. */
    val rimPow: Float,

    // ---- Accessories ----
    //
    // Everything below is zero-is-off, which is what keeps [CREAM] literally
    // unchanged and keeps `tools/check_look_sync.py` — whose PARAMS list is
    // explicit — from having to know about any of it. They are also the
    // first fields here that add *geometry*: a look with horns needs a mesh
    // built for it, so [BallLooks.ALL] is what the renderer iterates at surface
    // creation rather than one look.


    /**
     * Horizontal squash on the pupil, 1 = round.
     *
     * A vertical slit is the cheapest predator signal there is — one multiply,
     * and it changes *what kind of thing* is looking at you. Only meaningful
     * with [pupilR] non-zero.
     */
    val pupilAspect: Float = 1f,

    /**
     * Colour of the `^ ^` closed eye, when it should not be [eyeColor]. Null = use
     * [eyeColor], which is every look but two.
     *
     * Exists because "white eyes" is two different things. Awake, a white eye is
     * a white *shape* with a dark pupil in it. Asleep the shape is gone and what
     * is left is the line — and a white line on a dark body is a harsh slash with
     * no pupil to give it a reason, which is what the rabbit and the bear were
     * drawing. A dark line there is a lid, not a mark.
     */
    val sleepEyeColor: FloatArray? = null,

    // ---- Eye cut ----
    //
    // The glare, taken out of the eye's own geometry instead of drawn next to
    // it. See `gl/Eye` for why that is the whole point: a mark *over* the eye
    // has to be invisible against the body and visible against the eye at the
    // same time, and those two jobs have different scales ([browShade] is the
    // record of getting that wrong). A cut has no colour to get wrong.

    /**
     * How much of the eye's upper half is sliced off flat — the brow, made out
     * of the eye's own top edge. 0 = the untouched capsule.
     *
     * A cut eye is a *shape* statement and it survives being drawn a thumb
     * wide, which is the same reason [browTilt] won the argument for the brow
     * bar. What it buys over the bar is that it cannot be invisible: the flat
     * edge always has the white of the eye under it and the body beside it.
     *
     * ## The ceiling is the pupil, and it is not obvious
     *
     * [Eye.MAX_TOP_CUT] (0.85) is only the mesh's own limit. The bite is much
     * earlier than that, because the pupil and the glint are **separate discs
     * drawn in front of the eye** — the cut slices the eye and nothing else, so
     * a cut below the pupil's top leaves a dark blob standing up out of a flat
     * lid. In eye-local units, before the lid scale:
     *
     *  * the eye's half-height is `eyeRadius + eyeCyl / 2`;
     *  * the pupil's top is `0.8 × pupilR` above the eye's centre — its drawn
     *    centre is `0.2 × pupilR` low (see `drawEyeContents`) and its own
     *    half-height is `pupilR`.
     *
     * So the rule is
     * `eyeCut < 1 − 0.8 × pupilR / (eyeRadius + eyeCyl / 2)`.
     * For [IMP] (`pupilR` 0.082 against a half-height of 0.1755) that ceiling is
     * **0.626**, and the value it ships with is 0.48 — which leaves a band of
     * white 0.0257 tall above the pupil. Cutting to 0.626 exactly makes the
     * pupil tangent to the lid, which reads as the eye having no white at all.
     *
     * `tools/eye_mesh_check.py` runs this arithmetic against the actual mesh
     * generator, so the numbers above are measured rather than derived twice.
     *
     * The box's `^` eye is unaffected: it is a different mesh and is drawn
     * instead of this one, not inside it.
     */
    val eyeCut: Float = 0f,

    /**
     * Resting roll of the whole eye, degrees, inner end **down** for positive
     * values. 0 = level eyes.
     *
     * Not decoration. A flat cut across a level eye is a *sleepy* eye; the same
     * cut rolled a few degrees is a glare, because the line now points down at
     * the nose the way a lowered brow does. This is the angle that does it, and
     * it is why the cut lives in the mesh but the tilt does not.
     *
     * It is also the reason the imp needed no brow mark at all for the read to
     * survive: this field plus [EmotionState.brow] (which rolls the eyes another
     * `0.4 rad` at full anger) compose, so the resting scowl deepens when the
     * mood turns — the same lever [browTilt] had, on a shape that cannot
     * disappear.
     *
     * Deliberately smaller than the bar it replaces ([IMP] had 24°). Rotating a
     * *brow* moves a thin stroke; rotating the *eye* also swings its lower edge
     * and turns the pupil, and at 24° the near-round eye stops looking rolled
     * and starts looking deranged.
     */
    val eyeTilt: Float = 0f,

    /**
     * Resting roll of the whole mouth, degrees, **counter-clockwise** — which on
     * screen lifts the ball's right corner and drops its left. 0 = level.
     *
     * The cheapest smirk there is, and the reason it is a *look* field rather
     * than a mood one is that a smirk is who the character is, not how it feels:
     * [EmotionState] already rolls the mouth by `skew * 2.2` for the moods that
     * want a crooked mouth for a moment, and this simply moves where that starts
     * from. The two add, so a mood can still deepen or straighten it.
     *
     * It is the one asymmetry on an otherwise mirrored face, and that is the
     * whole value: every other feature here is drawn twice about the centre
     * line, so a face wearing this is the only one that cannot read as a
     * *diagram* of a feeling. Keep it small — the mouth is an arc, so the corner
     * travels `mouthR × sin θ`, and past about 15° the lower corner dips below
     * the arc's own middle and the mouth stops reading as a mouth.
     *
     * Anything that rides the mouth — [buckH], [tongueR], [toothCount] — is
     * placed through `EmotionBallRenderer.mouthPointAt`, which applies the live
     * roll, so all of it tilts with the smirk for free.
     */
    val mouthTilt: Float = 0f,

    // ---- Horns ----
    //
    // Parented to the body, not the face: they are part of the silhouette and
    // have to lean with the head tilt, breathe with the body and sink with the
    // cling. Lit with the shell shader, because a flat-shaded horn is a hole
    // where it crosses the ball.
    /** Length along the curved axis. 0 = no horns, and the mesh isn't built. */
    val hornH: Float = 0f,
    val hornR: Float = 0f,

    /** Base position on the body, mirrored for the other side. */
    val hornX: Float = 0f,
    val hornY: Float = 0f,

    /**
     * Pushed forward of the body's own surface so the base is not half-buried.
     * The body is a sphere, so a horn planted at z = 0 enters it immediately.
     */
    val hornZ: Float = 0f,

    /** Outward lean of the whole horn, degrees. */
    val hornTilt: Float = 0f,

    /** Additional outward curve along the length, degrees. See `gl/Horn`. */
    val hornBend: Float = 0f,
    val hornColor: FloatArray = EMPTY,

    /**
     * Profile exponent handed to `Horn.build`. 1 = the straight thorn every look
     * had before this field; above 1 the horn holds its width and rounds off, a
     * blunt chunky horn instead of a spike.
     */
    val hornTaper: Float = 1f,

    /**
     * Colour the horn as this multiple of the *live* body colour, like [earShade],
     * instead of the fixed [hornColor]. 0 = use [hornColor].
     *
     * Exists for a horn that is part of the animal rather than bone: a fixed colour
     * is right for the imp's, whose horns do not follow the mood, and wrong for a
     * horn the same blue as the head, where it would drift out of match every time
     * the mood tint moved the body.
     */
    val hornShade: Float = 0f,

    /**
     * A second colour the horn fades into over its last stretch, and how much of it.
     * 0 = the horn is one colour, which is four of the six looks — only the two with
     * horns have a tip colour at all, and the other four have ears instead.
     *
     * Per-vertex rather than a second mesh: the fade is along the horn's own length
     * (the mesh's v coordinate), so it follows the curl and the sway for free. It is
     * the cheapest way to make a horn read as *soft* — a one-colour blue horn is a
     * piece of the body, a horn whose tip blushes is something with blood in it.
     */
    val hornTipColor: FloatArray = EMPTY,
    val hornTipMix: Float = 0f,

    /**
     * 0 = the horns are rigid. Above that they hang off the body on a spring, like
     * jelly: a wiggle or a turn of the head sends them swinging the other way and
     * they ring for a moment before they settle, and the mood sets where they rest
     * (see [Pose.hornLean] — perked up when curious, flopped when sad). The number
     * scales how hard the body's motion drives them; 1 is a visible but gentle sway.
     */
    val hornWobble: Float = 0f,

    // ---- Second catchlight ----
    /**
     * A second, smaller highlight low and to the right of the eye — the one that
     * makes a dark eye look like glass rather than paint. 0 = none, which is every
     * look but one. Only drawn on a look with no pupil: with a pupil the glint is
     * anchored to it and a second one has nowhere obvious to sit.
     */
    val glint2R: Float = 0f,
    val glint2Color: FloatArray = EMPTY,

    // ---- Gestures ----
    /**
     * Draw props and honour the per-mood [Pose]: a wink, eyes that look up, a lid
     * that comes down, an open mouth with a tongue in it, a body that squashes. It
     * is also the gate on [hornWobble] (see [EmotionState.tick]) and the switch for
     * the idle life — a look with this on wears its own expressions and naps while
     * nothing is happening.
     *
     * **On for every look.** It was one look's for a while, on the argument that the
     * others had been tuned without any of it and a star over a pig's head was never
     * part of the brief. That is overruled: the brief now covers the pig too, and a
     * look that is not alive standing next to one that is reads as the broken one.
     *
     * Kept as a flag rather than deleted for two reasons: "off means the whole pose
     * table is ignored, not partly applied" is still the honest description of what a
     * look without it does, and a seventh look may well want the plain face back.
     *
     * It used to also gate the raised-hand poses. Those are gone — see [Pose].
     */
    val gestures: Boolean = false,

    /**
     * Colour the rim is pulled toward, by [rimTintMix]. Empty/0 = the rim is the
     * mood colour, as it always was.
     *
     * The mood colour is the right answer when the mood is the only thing on the
     * rim, and the wrong one for a character whose own outline is part of what it
     * looks like: the reference is edged in coral whatever it is feeling. The mix is
     * partial rather than total so the work states still have their own hue.
     */
    val rimTint: FloatArray = EMPTY,
    val rimTintMix: Float = 0f,

    /**
     * Multiplier on the scan band swept across the rim sphere while thinking or
     * speaking. 1 = unchanged. The band is additive light laid across the *whole*
     * sphere, face included, which is a bright stripe over a dark ball and a white
     * stripe through the eyes on a bright one — so a look with big glossy eyes
     * turns it nearly off.
     */
    val scanGain: Float = 1f,

    /**
     * Size of the `^ ^` closed eye, as a multiple of the default, which was drawn
     * for eyes of radius ~0.12. A look with eyes half as big again would otherwise
     * close them into two small ticks.
     */
    val arcScale: Float = 1f,

    /**
     * The mouth line's colour, fixed, instead of a shade of the mood. Empty = the
     * old rule ([mouthDeep]).
     *
     * A fixed ink is what a drawn character has: the reference's smile is the same
     * near-black in every expression. The mood-derived shade is a dark *purple* on
     * a calm blue ball and a dark *red* on an angry one, which reads as the mouth
     * having a mood of its own.
     */
    val mouthInk: FloatArray = EMPTY,

    /**
     * How far the mouth turns for each unit of [Face.smile], in radians. 0.9 is what
     * every look has always had.
     *
     * The turn is what makes a deep smile lopsided — one corner up, one down — which
     * is right for a smirk and wrong for a grin. It used to be hidden on the looks
     * that open their mouth; a plain line shows it in full, and a happy ball whose
     * mouth tilts thirty-five degrees reads as sneering. A look that wants a
     * symmetric smile sets this to 0 and gets its crookedness from [mouthTilt]
     * alone, which it can leave at 0 too.
     */
    val smileRoll: Float = 0.9f,

    /**
     * How far the eyes ride up the face while the ball hangs off the bottom edge.
     * 0.055 is what every look has always had.
     *
     * The edge cuts the ball at its equator, so only the top half shows, and a
     * look whose eyes sit below the middle — a baby's proportion, the whole face in
     * the lower half — would hang there with no face at all: a blue dome with horns.
     * Peering over the ledge is the one thing a clinging ball has to say, so such a
     * look lifts its eyes above the line while it clings and lets them settle back
     * when it lets go.
     */
    val clingLift: Float = 0.055f,

    // ---- Tail ----
    /** World length of the tail chain. 0 = no tail. */
    val tailLen: Float = 0f,

    /** Where it leaves the body, and which side (+1 = the ball's left on screen). */
    val tailX: Float = 0f,
    val tailY: Float = 0f,

    /** Radius of the first blob; the chain tapers to the barb. */
    val tailR: Float = 0f,

    /** Barb length and base radius. */
    val barbH: Float = 0f,
    val barbR: Float = 0f,

    // ---- Teeth ----
    /**
     * A row of triangles straddling the mouth line. 0 = none.
     *
     * A *row*, not a pair of fangs, and the difference is the whole read: two
     * spikes hanging off a small smile is a vampire, or — at the size this is
     * drawn — two white specks nobody can name. A zigzag of interlocking
     * triangles along the whole grin is the one mouth shape that says "monster"
     * at a thumbnail, and it says it while the mouth stays *smiling*, which is
     * where the cute half comes from.
     *
     * They alternate outward/inward along the arc, so consecutive teeth
     * interlock across the lip the way the top and bottom rows of a cartoon maw
     * do. Odd counts therefore look better than even ones: the middle tooth
     * lands on the centre line instead of the gap.
     */
    val toothCount: Int = 0,
    val toothH: Float = 0f,
    val toothR: Float = 0f,
    val toothColor: FloatArray = EMPTY,

    // ---- Buck teeth ----
    //
    // Two of them, in the middle of the mouth, hanging over the lip line. A
    // separate accessory from [toothCount] rather than a count of 2, and the
    // reason is what that row of teeth actually is: `drawTeeth` spaces its
    // triangles evenly *across the whole grin* and alternates them in and out,
    // so asking it for two puts one spike at each corner of the mouth pointing
    // off the face. The shapes are different too — a maw is a zigzag of points,
    // a rodent's incisors are a pair of flat-bottomed slabs dead centre — and
    // the two are mutually exclusive on one face anyway.
    //
    // They are drawn *over* the lip, not peeking out from behind it, which is
    // how a cartoon draws them and is also the only version that survives being
    // drawn a thumb wide: two white blocks interrupting the mouth line read as
    // teeth, where two white slivers below it read as a highlight on the lip.

    /** Half-width of one tooth. The pair sits one half-width either side of the
     * mouth's middle, so the two just touch. 0 = no buck teeth. */
    val buckW: Float = 0f,

    /** Half-height. Also how far the pair is pushed down the mouth's normal —
     * see `EmotionBallRenderer.drawBuckTeeth`. */
    val buckH: Float = 0f,
    val buckColor: FloatArray = EMPTY,

    /**
     * A lolling tongue at the bottom of the grin. 0 = none, which is what every
     * look currently ships with.
     *
     * It is kept because it is the one accessory that turns a threat into a
     * face that is pleased with itself, and that is a real lever for any future
     * look — but it costs more than it looks like it should. A saturated disc is
     * the highest-contrast thing on an ink body, so it does not sit *inside* the
     * grin so much as take it over: [IMP] drew one for a day and the teeth, which
     * are what makes the mouth read as a mouth at all, became scenery behind it.
     * If you turn it on, expect to have to make it duller than seems right.
     */
    val tongueR: Float = 0f,

    /** Where along the grin it hangs, radians off centre. 0 = dead centre. */
    val tongueSkew: Float = 0f,
    val tongueColor: FloatArray = EMPTY,

    // ---- Brows ----
    //
    // A flattened blob rather than a bar: a scaled sphere is a lens, which is
    // a tapered brow, which is what you wanted anyway.
    /** Half-width of the brow. 0 = no brows. */
    val browW: Float = 0f,
    val browH: Float = 0f,

    /** Height above the eye centre. */
    val browY: Float = 0f,

    /**
     * Resting angle, degrees, inner end **down** for positive values.
     *
     * This is the single strongest "not friendly" signal on the face — stronger
     * than colour, because it is a shape and shape survives being drawn a thumb
     * wide (the same reason LISTENING got a gape). [EmotionState.brow] then adds
     * to it, so a mood can still make the resting scowl deeper or lift it.
     */
    val browTilt: Float = 0f,

    /**
     * Draw the brow as a *hole in the body* at this multiple of the body's flat
     * albedo, instead of as a mark in [eyeColor]. 0 = a drawn brow.
     *
     * The distinction is what makes an angry eye rather than an angry eyebrow.
     * A white bar above a white eye on a dark ball is a third white shape on a
     * face that already has two, and the eye underneath it stays round — which
     * is to say, stays friendly. A body-coloured wedge laid over the top inner
     * corner *removes* part of the eye, and the white that survives is a
     * slanted almond. That is how every drawn monster gets its glare, and it
     * costs the same one draw call the bar did.
     *
     * A multiple of the live body colour rather than a constant, because the
     * body takes the mood's tint: a fixed near-black would be right in CALM and
     * a visible smudge in TENSE.
     *
     * ## Picking the number — this is a trap, and it has been fallen into
     *
     * The brow is drawn with the **flat** shader, so its pixel value is
     * `albedo × browShade`. The face it sits on is drawn with the **lit** shell
     * shader, so its pixel value is `albedo × (gradient × (ambient + diffuse))`
     * — which at the brow's latitude works out to about `0.82 × albedo` on
     * every look, because the gradient there is ~1.0 and the light is overhead.
     *
     * So the two are *not* on the same scale, and a number that looks like "the
     * body, a shade up" is not one:
     *
     *  * `1.0` means **darker than the face** (1.0 against 0.82) — a brow that
     *    vanishes into the forehead.
     *  * `1.05` — what [IMP] shipped with first — is only **1.28×** the face.
     *    In 8-bit on an ink body that is a ~7/255 difference: invisible. The
     *    brow's ends and its top edge, the parts that cross the *body* rather
     *    than the eye, disappeared; all that survived was the notch it cut out
     *    of the white eye. Hence the report "眉毛和脸部颜色接近，看不清".
     *
     * The fix is to aim at the **middle of the range the brow has to work in**,
     * not at the body:
     *
     *  * it must read **lighter than the body** (or the ends vanish), and
     *  * it must read **darker than the eye** (or there is no wedge).
     *
     * The eye is white, so the target is the geometric midpoint between the lit
     * body and 1.0. For an ink body (lit ≈ 0.105) that midpoint is ≈ 0.32,
     * which is `2.5 × albedo`. That lands the brow at 3.05× the body *and* 3.1×
     * under the eye — within a hair of equidistant, which is the definition of
     * "reads against both". See the table in `drawBrows`' KDoc for the sweep.
     *
     * Because both the body and the eye scale with the mood, this ratio holds
     * in every mood — the *hue* is what follows the mood, which is the point.
     *
     * **This field is for dark bodies.** On a pale look the same kind of number
     * does not transfer at all: a cream body's albedo is already ~0.9, so 2.5
     * clips the brow to white and it stops being a mark. That is not a reason
     * to retune this number for such a look — it is a reason to leave
     * [browShade] at 0 and let the brow be drawn in [eyeColor], which is what
     * [BAJIE] does. The split is the same one the eyes already make:
     * a pale ball reads its features *dark on light*, a dark ball *light on
     * dark*, and the one thing that never works is a mark the same value as the
     * surface it is on.
     *
     * ## Which is exactly why a dark body cannot leave it at 0
     *
     * The `else` branch in `drawBrows` is [eyeColor], and a dark body's
     * [eyeColor] is **white** — that same split, one line further down. So on
     * [BEAR] the zero default did not mean "plain eyebrow", it meant two white
     * caterpillars across a brown forehead, which is what the first build of
     * that look shipped. [BAJIE] gets away with 0 because its body is pale and
     * its eyes are dark beads, so the fallback happens to be the colour a brow
     * wants.
     *
     * [BEAR] therefore sets it, and sets it **below 1** — the first look to go
     * that way. The measurements above are all about dragging a mark *up* off an
     * ink body; the arithmetic downward is the easier half, because the only
     * constraint is distance from the body (its brow clears the eye entirely, so
     * there is no second end to satisfy) and because the body and the mark take
     * the mood's tint together. 0.42 against a lit body of `bodyColor × 0.89` is
     * a hair over 2× under it, in every channel, in every mood.
     *
     * ## [IMP] stopped using it, and that is still deliberate
     *
     * It was the only non-zero value for a while, and it has moved to [eyeCut]:
     * the same glare, got by removing part of the eye rather than by laying a
     * mid-tone wedge over it. The wedge worked, but it only ever worked in the
     * middle — for *that* job the arithmetic above is a range to hit rather than
     * a number, and every mood moves both ends of it. Note that this is a
     * complaint about the wedge, not about the field: `tools/check_look_sync.py`
     * still does not compare [browShade], which was defensible when no Kotlin
     * look wrote the line and is now one more thing the lab cannot see.
     */
    val browShade: Float = 0f,

    // ---- Snout ----
    //
    // A pig's signature: a rounded muzzle protruding from the face, with two
    // nostrils. Drawn lit with the body's own material, so it reads as a bump
    // rather than a flat sticker — the vertical gradient and the specular land
    // on it differently than on the body sphere. Zero-is-off like everything
    // else in this section.

    /** Half-width / half-height / protrusion of the muzzle. 0 = no snout. */
    val snoutRx: Float = 0f,
    val snoutRy: Float = 0f,
    val snoutRz: Float = 0f,

    /** Centre of the muzzle on the face plane. */
    val snoutX: Float = 0f,
    val snoutY: Float = 0f,

    /** Radius of each nostril dot, and how far they sit off the muzzle centre. */
    val nostrilR: Float = 0f,
    val nostrilX: Float = 0f,

    /**
     * Multiplier on the body colour for the muzzle, the way [earShade] is for
     * the ears. 1 = the body's own flesh, which is what a pig's snout is and is
     * the default, so [BAJIE] is unchanged.
     *
     * Raise it above 1 for a bear. This is a *scalar*, so it can only lighten or
     * darken the body's hue and can never introduce one — which is why the
     * rabbit has no pink inner ear and why it is nevertheless the right
     * mechanism here: a bear's tan muzzle genuinely is the same brown as the
     * rest of it with more light in it, so [BEAR]'s 1.45 lands on the colour
     * the animal actually is rather than approximating it.
     *
     * There is headroom above 1 only because a look that wants this is a dark
     * one. On a pale body the product clips to flat white and the muzzle stops
     * being a bump — check the arithmetic before reusing this on anything light.
     */
    val snoutShade: Float = 1f,

    // ---- Ears ----
    //
    // A floppy leaf drooping down the side of the head. Parented to the body,
    // not the face, for the same reason the horns are: they are part of the
    // silhouette and have to lean with the head, breathe with the body and be
    // cut by the ledge scissor. Lit with the shell shader too, and made of the
    // body's flesh rather than a separate colour.

    /** Length along the curved axis. 0 = no ears, and the mesh isn't built. */
    val earH: Float = 0f,
    val earW: Float = 0f,
    val earThick: Float = 0f,

    /** Base position on the body, mirrored for the other side. */
    val earX: Float = 0f,
    val earY: Float = 0f,
    val earZ: Float = 0f,

    /** How far the ear leans off vertical, degrees. 0 = straight up. */
    val earTilt: Float = 0f,

    /** Additional droop curve along the length, degrees. See `gl/Ear`. */
    val earBend: Float = 0f,

    /**
     * How fast the flap narrows toward its tip: `√(1 − t^earTaper)`. See
     * `gl/Ear.build`, where the two numbers that matter are worked out.
     *
     * **2 is the quarter ellipse the ear mesh was born as**, and is the default,
     * so a look that does not mention this is drawn exactly as it was. Raise it
     * for a rabbit: at 2 a flap is a cone with a blunt end, and standing a cone
     * up gets you a horn however long you make it — which is how the first
     * 贱贱兔 shipped. The length and the lean were both already right; this was
     * the parameter that was missing.
     */
    val earTaper: Float = 2f,

    /**
     * Multiplier on the live body colour for the ear. 1 = the body's own colour.
     *
     * Not a tint for its own sake — on a **pale** look it is the only thing that
     * makes the ear visible. An ear is parented to the body and its tip grows
     * *past* the dark outline ([rimDark]), so past the silhouette there is no
     * dark line left to hold it apart from a light app; at 1.0 a cream ear on a
     * cream body reads as a soft bump nobody can name, and the ball grows two
     * dimples. Darkening the ear by a fixed fraction puts an edge on it without
     * drawing a second outline, and it is honest about the shape besides: the
     * underside of a curved flap *is* darker than the ball.
     *
     * Defaults to 1, so every look that does not mention it is drawn exactly as
     * it was — which is what keeps [BAJIE], whose hide is dark enough to hold
     * its own ears apart, untouched.
     */
    val earShade: Float = 1f,

    /**
     * Colour of an inner-ear patch drawn on the front of each ear, or null for
     * none. It is the same flap mesh scaled down toward the base and nudged
     * forward in Z, so it costs no new geometry. The patch is what gives a cat
     * ear depth: a single flat-coloured triangle reads as a horn or a fin.
     */
    val earInner: FloatArray? = null,

    // ---- Who it is ----
    //
    // The two fields that are not about drawing at all, and the reason the class
    // KDoc above no longer claims this file is "only the surface": a look is a
    // *character*, and a character that looks like an imp and then answers in
    // the house voice with the house manner is a costume rather than a
    // character. Both are zero-is-off like the accessories, so the two looks
    // that shipped before this keep the factory voice and the plain prompt
    // without having to name either.
    //
    // They live here — on the look — rather than in a table of their own keyed
    // by [name]. A second list keyed by the same strings is exactly the drift
    // this codebase already pays `tools/check_look_sync.py` to catch, and the
    // cost of avoiding it is one dependency pointing the odd way: `config` now
    // reads `ui.ball` (see `VoiceConfig.lookSpeaker`). `ui.ball` imports nothing
    // but `R` and its own `gl`, so there is no cycle to walk into.

    /**
     * 火山 TTS speaker id this character speaks with, or blank for the fallback
     * in `VoiceConfig.lookSpeaker` (the compiled-in [VoiceConfig.TTS_SPEAKER]).
     *
     * Blank is **not** "silent" — it means *this look has no opinion* and the
     * device falls back to the house voice. There is no settings row and no
     * override in the way: the voice is the character's, full stop, so filling
     * this in is the only voice control there is and the user always hears it
     * (a deliberate removal of the earlier three-way precedence — see the commit
     * that dropped the `tts_speaker` setting).
     *
     * Unlike [persona] this works in **both** brain modes, because TTS is the
     * device's own and `TtsController` re-reads the config per utterance.
     */
    val voice: String = "",

    /**
     * How this character talks, appended to the local brain's system prompt.
     * Blank = plain Andee.
     *
     * **Model-facing English**, like `LocalPrompt` and `ToolSchemas` and for the
     * same reason — see `LocalPrompt`'s KDoc on the split between what the model
     * reads and what the user reads. That is also why it is a plain string and
     * not a string resource: translating it would be translating the wrong half.
     *
     * Two limits worth knowing before writing one:
     *
     *  * **It only applies when the brain is this device.** In
     *    [net.kuafuai.andee.config.VoiceConfig.BRAIN_HUB] mode the prompt
     *    lives on the agentworld side and this repo does not own it, so a
     *    hub-backed imp looks like an imp, sounds like an imp, and answers
     *    like Andee.
     *  * **It is a manner, never a licence.** `LocalPrompt.personaBlock` wraps
     *    whatever goes in here with that caveat, so every future look inherits
     *    it; don't restate it per look, and don't write one that needs it
     *    relaxed.
     */
    val persona: String = "",
) {
    companion object {
        /** Stand-in for the colour of an accessory a look doesn't have. */
        private val EMPTY = floatArrayOf(0f, 0f, 0f)
    }
}

/** Original default avatar, preserved without changing its geometry or material. */
val ANDEE = BallLook(
    name = "andee",
    bodyBase = floatArrayOf(0.250f, 0.460f, 0.870f),
    bodyTint = 0.10f,
    bodyAlbedo = 1.00f,
    bodyGrad = 0.26f,
    bodyAmb = 0.94f,
    bodyDiff = 0.20f,
    // Soft, like the picture — a velvety plush, not lacquer. Low enough that
    // the crown does not grow a white blob that would fight the catchlights.
    bodyGloss = 0.05f,
    bodyScale = 0.870f,
    faceZ = 0.876f,
    eyeX = 0.345f,
    // Raised 0.12 from the picture's own −0.03, after the horns were raised twice:
    // with horns that tall the sphere read as a forehead dome with a face stuck on
    // the bottom of it — 1.7 eye-diameters of blank above the eyes against the
    // picture's 1.4. Eyes, mouth, cheeks and the sleep bubble all moved together so
    // the face is the same face, one notch higher.
    eyeY = 0.070f,
    eyeRadius = 0.215f,
    eyeCyl = 0.012f,
    mouthR = 0.115f,
    mouthTube = 0.028f,
    mouthY = -0.285f,
    mouthThick = 0.140f,
    eyeColor = floatArrayOf(0.035f, 0.080f, 0.280f),
    pupilR = 0f,
    glintR = 0.065f,
    glint2R = 0.050f,
    glint2Color = floatArrayOf(0.45f, 0.70f, 1.00f),
    gestures = true,
    // 0.09 now, from 0.17: the eyes already sit 0.12 higher, so the same lift would
    // put them well clear of the ledge and leave the peeking ball with no forehead.
    clingLift = 0.16f,
    smileRoll = 0f,
    mouthInk = floatArrayOf(0.025f, 0.045f, 0.140f),
    arcScale = 1.5f,
    scanGain = 0.12f,
    rimTint = floatArrayOf(1.0f, 0.62f, 0.50f),
    rimTintMix = 1.0f,
    // Light droplet on a mid-dark body, the same side of the split as [BEAR].
    bubbleX = 0.085f,
    bubbleY = 0.070f,
    bubbleR = 0.085f,
    bubbleColor = floatArrayOf(0.88f, 0.93f, 1.00f),
    bubblePow = 3.0f,
    bubbleShine = 26f,
    bubbleInner = 0.10f,
    mouthDeep = true,
    // The picture has no cheeks. Kept faintly so the happy moods still flush.
    blushColor = floatArrayOf(1f, 0.55f, 0.75f),
    blushAlpha = 0.10f,
    blushBlendNormal = true,
    blushSize = 0.40f,
    blushX = 0.440f,
    blushY = -0.010f,
    // A light rim, like the imp's and bear's: a dark outline on a mid-dark body
    // is a smudge.
    rimDark = false,
    rimGain = 1.30f,
    rimPow = 3.0f,

    // See the class note: fitted to the picture's horn, then raised twice on request
    // — the second time (0.477 → 0.53, tilt 50 → 38, bend −80 → −62, a longer horn)
    // makes them stand more upright as well as higher: the tip goes from y 0.89 to
    // about 1.02 and the sweep sideways shrinks. Base buried at (0.40, 0.53) so only
    // the curl shows.
    // The tip blushes toward the coral of the rim and the props. Blue and peach
    // mixed half and half is a dull lilac, so it goes nearly all the way (0.9) over
    // the last ~40% of the horn: the very end is peach, and only the short stretch
    // between is the in-between colour.
    hornH = 0.420f,
    hornR = 0.265f,
    hornX = 0.400f,
    hornY = 0.540f,
    hornZ = 0.220f,
    hornTilt = 30f,
    hornBend = -50f,
    hornTaper = 0.9f,
    hornShade = 1.00f,
    hornTipColor = floatArrayOf(1.00f, 0.70f, 0.58f),
    hornTipMix = 0.00f,
    // 1.5 rather than 1: measured on the device, a wiggle at 1 moves the horns about
    // 10 px against the eyes on a 420 dp ball, which is about 3° and at the real
    // 168 dp size is a pixel and a half — present, and not visible.
    hornWobble = 1.5f,
)

/** The original companion stays first; old saved looks migrate back to it. */
object BallLooks {
    val ALL = listOf(ANDEE, MOSS, NOVA)

    @Volatile private var requested = 0
    @Volatile var index = 0
        private set

    val active: BallLook get() = ALL[index]
    val requestedLook: BallLook get() = ALL[requested]

    fun request(i: Int) {
        requested = ((i % ALL.size) + ALL.size) % ALL.size
    }

    fun step(delta: Int): BallLook {
        request(requested + delta)
        return requestedLook
    }

    fun requestByName(name: String?) {
        request(ALL.indexOfFirst { it.name == name }.coerceAtLeast(0))
    }

    fun byName(name: String?): BallLook = ALL.firstOrNull { it.name == name } ?: ALL[0]

    /** Legacy shared selection; each renderer snapshots its own look for an entire frame. */
    fun latch(): Boolean {
        val next = requested
        if (next == index) return false
        index = next
        return true
    }
}
