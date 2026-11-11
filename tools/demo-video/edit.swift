// 시연 영상 편집기 (macOS 내장 AVFoundation 만 사용)
//   frames <video> <outdir> <t1> <t2> ...           지정 시각 프레임을 PNG 로
//   render <video> <plan.json> <audio.wav> <out.mp4>  구간 잘라 붙이기 + 자막/타이틀 카드 + 오디오
import AVFoundation
import AppKit
import QuartzCore

let args = CommandLine.arguments

func fail(_ m: String) -> Never { FileHandle.standardError.write((m + "\n").data(using: .utf8)!); exit(1) }

// ---------------- frames ----------------
func frames() {
    let asset = AVURLAsset(url: URL(fileURLWithPath: args[2]))
    let gen = AVAssetImageGenerator(asset: asset)
    gen.requestedTimeToleranceBefore = .zero
    gen.requestedTimeToleranceAfter = .zero
    gen.maximumSize = CGSize(width: 960, height: 540)
    for s in args[4...] {
        let t = Double(s)!
        do {
            let img = try gen.copyCGImage(at: CMTime(seconds: t, preferredTimescale: 600), actualTime: nil)
            let rep = NSBitmapImageRep(cgImage: img)
            let png = rep.representation(using: .png, properties: [:])!
            try png.write(to: URL(fileURLWithPath: "\(args[3])/f_\(s).png"))
        } catch { fail("frame \(s): \(error)") }
    }
}

// ---------------- render ----------------
struct Seg: Decodable { let start: Double; let end: Double }
struct Cap: Decodable { let t0: Double; let t1: Double; let text: String }
struct Card: Decodable { let t0: Double; let t1: Double; let title: String; let sub: String; let lines: [String]; let opacity: Double }
struct Plan: Decodable { let segments: [Seg]; let captions: [Cap]; let cards: [Card] }

var W: CGFloat = 1920, H: CGFloat = 1080
let gold = CGColor(red: 1, green: 0.827, blue: 0.302, alpha: 1)

func font(_ size: CGFloat, bold: Bool = true) -> NSFont {
    NSFont(name: bold ? "AppleSDGothicNeo-Bold" : "AppleSDGothicNeo-Medium", size: size) ?? NSFont.boldSystemFont(ofSize: size)
}

/** 글자를 직접 비트맵으로 그려 이미지 레이어로 (CATextLayer 는 오프라인 렌더에서 글자가 안 그려짐) */
func textLayer(_ str: String, size: CGFloat, color: CGColor, bold: Bool = true, width maxWidth: CGFloat) -> CALayer {
    let f = font(size, bold: bold)
    let para = NSMutableParagraphStyle(); para.alignment = .center; para.lineSpacing = size * 0.18
    let attr = NSAttributedString(string: str, attributes: [.font: f, .foregroundColor: NSColor(cgColor: color)!, .paragraphStyle: para])
    let r = attr.boundingRect(with: CGSize(width: maxWidth, height: 4000), options: [.usesLineFragmentOrigin, .usesFontLeading])
    let w = ceil(r.width) + 8, h = ceil(r.height) + 8
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(w * 2), pixelsHigh: Int(h * 2), bitsPerSample: 8,
                               samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                               bytesPerRow: 0, bitsPerPixel: 0)!
    rep.size = NSSize(width: w, height: h)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    attr.draw(with: NSRect(x: 4, y: 4, width: w - 8, height: h - 8), options: [.usesLineFragmentOrigin, .usesFontLeading])
    NSGraphicsContext.restoreGraphicsState()
    let l = CALayer()
    l.contents = rep.cgImage
    l.contentsGravity = .resize
    l.frame = CGRect(x: 0, y: 0, width: w, height: h)
    return l
}

/** t0~t1 동안만 보이게 (페이드 0.25초) */
func showDuring(_ layer: CALayer, _ t0: Double, _ t1: Double, maxOpacity: Float = 1) {
    layer.opacity = 0
    let a = CAKeyframeAnimation(keyPath: "opacity")
    let d = t1 - t0
    let f = min(0.25, d / 4)
    a.values = [0, maxOpacity, maxOpacity, 0]
    a.keyTimes = [0, NSNumber(value: f / d), NSNumber(value: 1 - f / d), 1]
    a.beginTime = t0 <= 0 ? AVCoreAnimationBeginTimeAtZero : t0
    a.duration = d
    a.isRemovedOnCompletion = false
    a.fillMode = .forwards
    layer.add(a, forKey: "show")
}

func captionLayer(_ c: Cap) -> CALayer {
    let t = textLayer(c.text, size: 50, color: CGColor.white, width: 1500)
    let padX: CGFloat = 40, padY: CGFloat = 18
    let box = CALayer()
    box.backgroundColor = CGColor(red: 0.04, green: 0.04, blue: 0.08, alpha: 0.82)
    box.cornerRadius = 22
    box.borderColor = CGColor(red: 1, green: 0.83, blue: 0.3, alpha: 0.55)
    box.borderWidth = 2
    let bw = t.frame.width + padX * 2, bh = t.frame.height + padY * 2
    box.frame = CGRect(x: (W - bw) / 2, y: 150, width: bw, height: bh)   // 원점 좌하단, 하단 내비바 위
    t.frame.origin = CGPoint(x: padX, y: padY)
    box.addSublayer(t)
    showDuring(box, c.t0, c.t1)
    return box
}

func cardLayer(_ c: Card) -> CALayer {
    let root = CALayer()
    root.frame = CGRect(x: 0, y: 0, width: W, height: H)
    let bg = CAGradientLayer()
    bg.frame = root.bounds
    bg.colors = [CGColor(red: 0.10, green: 0.11, blue: 0.20, alpha: 1), CGColor(red: 0.02, green: 0.02, blue: 0.05, alpha: 1)]
    bg.type = .radial
    bg.startPoint = CGPoint(x: 0.5, y: 0.5); bg.endPoint = CGPoint(x: 1.2, y: 1.2)
    root.addSublayer(bg)
    // 위에서 아래로 실제 높이만큼 쌓기 (여러 줄 부제가 제목과 겹치지 않게)
    let title = textLayer(c.title, size: 128, color: gold, width: min(1700, W - 120))
    let sub = textLayer(c.sub, size: 46, color: CGColor(gray: 0.88, alpha: 1), bold: false, width: min(1700, W - 120))
    let lines = c.lines.map { textLayer($0, size: 38, color: CGColor(red: 0.6, green: 0.88, blue: 1, alpha: 1), bold: false, width: min(1700, W - 120)) }
    let gapTitle: CGFloat = 36, gapSub: CGFloat = 48, gapLine: CGFloat = 18
    let block = title.frame.height + gapTitle + sub.frame.height + gapSub + lines.reduce(0) { $0 + $1.frame.height + gapLine }
    var y = (H + block) / 2                          // 블록 윗변 (원점 좌하단)
    y -= title.frame.height; title.frame.origin = CGPoint(x: (W - title.frame.width) / 2, y: y); root.addSublayer(title)
    y -= gapTitle + sub.frame.height; sub.frame.origin = CGPoint(x: (W - sub.frame.width) / 2, y: y); root.addSublayer(sub)
    y -= gapSub
    for l in lines {
        y -= l.frame.height; l.frame.origin = CGPoint(x: (W - l.frame.width) / 2, y: y); root.addSublayer(l); y -= gapLine
    }
    showDuring(root, c.t0, c.t1, maxOpacity: Float(c.opacity))
    return root
}

func render() {
    let src = AVURLAsset(url: URL(fileURLWithPath: args[2]))
    let plan = try! JSONDecoder().decode(Plan.self, from: Data(contentsOf: URL(fileURLWithPath: args[3])))
    let audio = AVURLAsset(url: URL(fileURLWithPath: args[4]))
    let out = URL(fileURLWithPath: args[5])
    try? FileManager.default.removeItem(at: out)

    guard let vSrc = src.tracks(withMediaType: .video).first else { fail("no video track") }
    let comp = AVMutableComposition()
    let vTrack = comp.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)!
    var cursor = CMTime.zero
    for s in plan.segments {
        let r = CMTimeRange(start: CMTime(seconds: s.start, preferredTimescale: 600),
                            end: CMTime(seconds: s.end, preferredTimescale: 600))
        try! vTrack.insertTimeRange(r, of: vSrc, at: cursor)
        cursor = cursor + r.duration
    }
    let total = cursor
    if let aSrc = audio.tracks(withMediaType: .audio).first {
        let aTrack = comp.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)!
        let len = min(audio.duration, total)
        try! aTrack.insertTimeRange(CMTimeRange(start: .zero, duration: len), of: aSrc, at: .zero)
    }

    let parent = CALayer(); parent.frame = CGRect(x: 0, y: 0, width: W, height: H)
    let videoLayer = CALayer(); videoLayer.frame = parent.frame
    parent.addSublayer(videoLayer)
    plan.cards.forEach { parent.addSublayer(cardLayer($0)) }
    plan.captions.forEach { parent.addSublayer(captionLayer($0)) }

    let vc = AVMutableVideoComposition()
    vc.renderSize = CGSize(width: W, height: H)
    vc.frameDuration = CMTime(value: 1, timescale: 30)
    let ins = AVMutableVideoCompositionInstruction()
    ins.timeRange = CMTimeRange(start: .zero, duration: total)
    let li = AVMutableVideoCompositionLayerInstruction(assetTrack: vTrack)
    let natural = vSrc.naturalSize
    li.setTransform(CGAffineTransform(scaleX: W / natural.width, y: H / natural.height), at: .zero)
    ins.layerInstructions = [li]
    vc.instructions = [ins]
    vc.animationTool = AVVideoCompositionCoreAnimationTool(postProcessingAsVideoLayer: videoLayer, in: parent)

    guard let ex = AVAssetExportSession(asset: comp, presetName: AVAssetExportPreset1920x1080) else { fail("no export session") }
    ex.outputURL = out
    ex.outputFileType = .mp4
    ex.videoComposition = vc
    let sem = DispatchSemaphore(value: 0)
    ex.exportAsynchronously { sem.signal() }
    sem.wait()
    if ex.status != .completed { fail("export failed: \(String(describing: ex.error))") }
    print("ok \(String(format: "%.1f", total.seconds))s -> \(out.path)")
}


// ---------------- vrender (세로 9:16 숏폼) ----------------
struct VPlan: Decodable {
    let segments: [Seg]; let headlines: [Cap]; let captions: [Cap]; let cards: [Card]
    let crop: [Double]; let videoTop: Double; let footer: String; let brand: String
}

func vrender() {
    W = 1080; H = 1920
    let src = AVURLAsset(url: URL(fileURLWithPath: args[2]))
    let plan = try! JSONDecoder().decode(VPlan.self, from: Data(contentsOf: URL(fileURLWithPath: args[3])))
    let audio = AVURLAsset(url: URL(fileURLWithPath: args[4]))
    let out = URL(fileURLWithPath: args[5])
    try? FileManager.default.removeItem(at: out)
    guard let vSrc = src.tracks(withMediaType: .video).first else { fail("no video track") }

    let comp = AVMutableComposition()
    let vTrack = comp.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)!
    var cursor = CMTime.zero
    for s in plan.segments {
        let r = CMTimeRange(start: CMTime(seconds: s.start, preferredTimescale: 600), end: CMTime(seconds: s.end, preferredTimescale: 600))
        try! vTrack.insertTimeRange(r, of: vSrc, at: cursor)
        cursor = cursor + r.duration
    }
    let total = cursor
    if let aSrc = audio.tracks(withMediaType: .audio).first {
        let aTrack = comp.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)!
        try! aTrack.insertTimeRange(CMTimeRange(start: .zero, duration: min(audio.duration, total)), of: aSrc, at: .zero)
    }

    // 원본(1920x1080)에서 앱 화면만 잘라 가로 폭에 맞춤. 영상 좌표는 원점 좌상단
    let crop = CGRect(x: plan.crop[0], y: plan.crop[1], width: plan.crop[2], height: plan.crop[3])
    let sc = W / crop.width
    let vidH = crop.height * sc
    let top = CGFloat(plan.videoTop)
    let li = AVMutableVideoCompositionLayerInstruction(assetTrack: vTrack)
    li.setCropRectangle(crop, at: .zero)
    li.setTransform(CGAffineTransform(translationX: -crop.minX, y: -crop.minY)
        .concatenating(CGAffineTransform(scaleX: sc, y: sc))
        .concatenating(CGAffineTransform(translationX: 0, y: top)), at: .zero)

    // 오버레이 (CALayer 원점 좌하단)
    let parent = CALayer(); parent.frame = CGRect(x: 0, y: 0, width: W, height: H)
    let videoLayer = CALayer(); videoLayer.frame = parent.frame
    parent.addSublayer(videoLayer)
    let videoTopY = H - top                  // 영상 윗변 (CALayer 좌표)
    let videoBottomY = H - top - vidH        // 영상 아랫변
    let dark1 = CGColor(red: 0.08, green: 0.09, blue: 0.16, alpha: 1), dark2 = CGColor(red: 0.02, green: 0.02, blue: 0.05, alpha: 1)
    let topBand = CAGradientLayer(); topBand.frame = CGRect(x: 0, y: videoTopY, width: W, height: top)
    topBand.colors = [dark1, dark2]; parent.addSublayer(topBand)
    let botBand = CAGradientLayer(); botBand.frame = CGRect(x: 0, y: 0, width: W, height: videoBottomY)
    botBand.colors = [dark2, dark1]; parent.addSublayer(botBand)
    for y in [videoTopY, videoBottomY - 3] {                 // 영상 위아래 금색 라인
        let line = CALayer(); line.frame = CGRect(x: 0, y: y, width: W, height: 3); line.backgroundColor = gold; parent.addSublayer(line)
    }
    let brand = textLayer(plan.brand, size: 40, color: gold, width: W - 120)
    brand.frame.origin = CGPoint(x: (W - brand.frame.width) / 2, y: H - 150); parent.addSublayer(brand)
    let foot = textLayer(plan.footer, size: 32, color: CGColor(gray: 0.72, alpha: 1), bold: false, width: W - 100)
    foot.frame.origin = CGPoint(x: (W - foot.frame.width) / 2, y: 80); parent.addSublayer(foot)
    for h in plan.headlines {
        let l = textLayer(h.text, size: 78, color: CGColor.white, width: W - 100)
        let bandBottom = videoTopY, bandTop = H - 170
        l.frame.origin = CGPoint(x: (W - l.frame.width) / 2, y: bandBottom + (bandTop - bandBottom - l.frame.height) / 2)
        showDuring(l, h.t0, h.t1); parent.addSublayer(l)
    }
    for c in plan.captions {
        let l = textLayer(c.text, size: 50, color: CGColor(red: 1, green: 0.86, blue: 0.45, alpha: 1), width: W - 120)
        let bandBottom: CGFloat = 160, bandTop = videoBottomY
        l.frame.origin = CGPoint(x: (W - l.frame.width) / 2, y: bandBottom + (bandTop - bandBottom - l.frame.height) / 2)
        showDuring(l, c.t0, c.t1); parent.addSublayer(l)
    }
    plan.cards.forEach { parent.addSublayer(cardLayer($0)) }

    let vc = AVMutableVideoComposition()
    vc.renderSize = CGSize(width: W, height: H)
    vc.frameDuration = CMTime(value: 1, timescale: 30)
    let ins = AVMutableVideoCompositionInstruction()
    ins.timeRange = CMTimeRange(start: .zero, duration: total)
    ins.backgroundColor = dark2
    ins.layerInstructions = [li]
    vc.instructions = [ins]
    vc.animationTool = AVVideoCompositionCoreAnimationTool(postProcessingAsVideoLayer: videoLayer, in: parent)

    guard let ex = AVAssetExportSession(asset: comp, presetName: AVAssetExportPresetHighestQuality) else { fail("no export session") }
    ex.outputURL = out; ex.outputFileType = .mp4; ex.videoComposition = vc
    let sem = DispatchSemaphore(value: 0)
    ex.exportAsynchronously { sem.signal() }
    sem.wait()
    if ex.status != .completed { fail("export failed: \(String(describing: ex.error))") }
    print("ok vertical \(String(format: "%.1f", total.seconds))s -> \(out.path)")
}

switch args.count > 1 ? args[1] : "" {
case "frames": frames()
case "render": render()
case "vrender": vrender()
default: fail("usage: frames|render ...")
}
