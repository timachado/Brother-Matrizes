from pathlib import Path
import sys,re
root=Path(sys.argv[1]); p=root/'app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt'
s=p.read_text(encoding='utf-8')
assert 'private var exportWhenReady' in s
s=s.replace('import android.graphics.Color','import android.content.res.ColorStateList\nimport android.graphics.Color',1)
s=s.replace('import android.widget.*','import android.widget.*\nimport com.google.android.material.button.MaterialButton\nimport com.google.android.material.card.MaterialCardView',1)
old='''        statusLabel = text("Pronto. Nenhum áudio sai do aparelho.", 13f, false)
        root.addView(statusLabel, full(wrap(), top = 8))
        applyTheme()'''
new='''        statusLabel = text("Pronto. Nenhum áudio sai do aparelho.", 13f, false)
        root.addView(statusLabel, full(wrap(), top = 8))
        reflowExpressiveUi()
        applyTheme()'''
assert s.count(old)==1
s=s.replace(old,new,1)
start=s.index('    private fun applyTheme() {')
end=s.index('    private fun simpleSeek(',start)
style='''    private fun reflowExpressiveUi() {
        val nodes = (0 until root.childCount).map { root.getChildAt(it) }
        // O layout base tem 32 blocos; não reorganizar se outra versão divergir.
        if (nodes.size != 32) return
        root.removeAllViews()
        val header = nodes[0] as LinearLayout
        val brand = ImageView(this).apply {
            setImageResource(R.drawable.ic_pitchstudio_mark)
            contentDescription = "Marca de onda sonora PitchStudio"
            setPadding(dp(2),dp(2),dp(2),dp(2))
        }
        header.addView(brand,0,LinearLayout.LayoutParams(dp(50),dp(50)).apply { rightMargin=dp(9) })
        val heading = header.getChildAt(1) as? TextView
        heading?.apply { text = "PitchStudio"; textSize=23f; setTypeface(null,Typeface.BOLD) }
        root.addView(header,full(wrap(),top=5))

        fun group(indices: List<Int>, kicker: String, headline: String? = null, mode: String = "surface") {
            val panel = MaterialCardView(this).apply {
                tag=mode; radius=dp(if(mode=="hero") 30 else 24).toFloat()
                strokeWidth=dp(1); cardElevation=0f
            }
            val column = LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(dp(17),dp(16),dp(17),dp(18))
            }
            column.addView(text(kicker,12f,true),full(wrap()))
            if(headline!=null) column.addView(text(headline,if(mode=="hero") 24f else 18f,true),full(wrap(),top=6))
            for(i in indices) {
                val child=nodes[i]
                val lp=child.layoutParams as? LinearLayout.LayoutParams
                val height=lp?.height ?: wrap()
                column.addView(child,full(height,top=if(i==indices.first()) 10 else 5))
            }
            panel.addView(column)
            root.addView(panel,full(wrap(),top=13))
        }
        group(listOf(1,2,4,5),"COMECE POR AQUI","Transforme cada nota.","hero")
        group(listOf(3,6,7,8),"AGORA NO ESTÚDIO","Ouça suas alterações")
        group((9..14).toList(),"TRANSFORMAÇÃO")
        val presetRow=nodes[16] as? LinearLayout
        if(presetRow != null && presetRow.childCount == 4) {
            val buttons=(0..3).map {presetRow.getChildAt(it)}
            presetRow.removeAllViews()
            val block=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
            for(offset in listOf(0,2)) {
                val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
                for(i in offset until offset+2) {
                    row.addView(buttons[i],LinearLayout.LayoutParams(0,dp(49),1f).apply {
                        setMargins(dp(3),0,dp(3),0)
                    })
                }
                block.addView(row,full(wrap(),top=if(offset==0) 0 else 6))
            }
            presetRow.addView(block,LinearLayout.LayoutParams(-1,-2))
        }
        group(listOf(15,16),"ATALHOS MUSICAIS")
        group((17..23).toList(),"CONTROLES DE ÁUDIO")
        group((24..27).toList(),"FERRAMENTAS")
        group(listOf(28,29),"FINALIZAR","Sua música pronta para usar.","hero")
        for(i in 30..31) root.addView(nodes[i],full(nodes[i].layoutParams?.height?:wrap(),top=9))
    }

    private fun applyTheme() {
        val bg=if(dark) Color.rgb(7,13,25) else Color.rgb(247,249,253)
        val fg=if(dark) Color.rgb(243,246,255) else Color.rgb(23,31,50)
        val muted=if(dark) Color.rgb(173,189,210) else Color.rgb(79,95,119)
        val accent=if(dark) Color.rgb(43,219,230) else Color.rgb(8,120,158)
        val surface=if(dark) Color.rgb(18,28,47) else Color.WHITE
        val surfaceAlt=if(dark) Color.rgb(35,39,72) else Color.rgb(229,232,255)
        val secondary=if(dark) Color.rgb(43,57,83) else Color.rgb(222,232,246)
        root.setBackgroundColor(bg)
        waveform.dark=dark
        walk(root){ v -> when(v){
            is MaterialCardView -> {
                v.setCardBackgroundColor(if(v.tag=="hero") surfaceAlt else surface)
                v.strokeColor=if(dark) Color.rgb(52,63,92) else Color.rgb(218,224,240)
            }
            is MaterialButton -> {
                val important= v.text.toString().contains("Selecionar música") || v.text.toString().contains("Reproduzir") || v.text.toString().contains("WAV")
                v.backgroundTintList=ColorStateList.valueOf(if(important) accent else secondary)
                v.setTextColor(if(important) bg else fg)
            }
            is TextView -> v.setTextColor(if(v.textSize>=17*resources.displayMetrics.scaledDensity) fg else muted)
            is SeekBar -> {
                v.progressTintList=ColorStateList.valueOf(accent)
                v.thumbTintList=ColorStateList.valueOf(accent)
                v.progressBackgroundTintList=ColorStateList.valueOf(secondary)
            }
        }}
        window.statusBarColor=bg
        window.navigationBarColor=bg
        window.decorView.systemUiVisibility=if(dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    private fun walk(v:View,action:(View)->Unit){
        action(v)
        if(v is ViewGroup) for(i in 0 until v.childCount) walk(v.getChildAt(i),action)
    }
    private fun section(title:String):TextView=text(title,17f,true).apply{setPadding(0,dp(15),0,dp(8))}
    private fun text(value:String,sp:Float,bold:Boolean)=TextView(this).apply{
        text=value; textSize=sp
        if(bold) setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL),Typeface.BOLD)
        setLineSpacing(0f,1.08f)
    }
    private fun button(label:String,click:()->Unit)=MaterialButton(this).apply{
        text=label; isAllCaps=false
        cornerRadius=dp(23); minHeight=dp(48)
        setOnClickListener{click()}
    }
    private fun row(gravityValue:Int=Gravity.CENTER_VERTICAL)=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=gravityValue
    }
'''
s=s[:start]+style+s[end:]
s=s.replace('setPadding(dp(16), dp(14), dp(16), dp(28))','setPadding(dp(18), dp(14), dp(18), dp(36))',1)
s=s.replace('"Importar link de áudio"','"Importar link"',1)
p.write_text(s,encoding='utf-8')

p=root/'app/build.gradle.kts';g=p.read_text(encoding='utf-8')
assert 'versionCode = 12' in g and 'versionName = "1.9.2"' in g
g=g.replace('versionCode = 12','versionCode = 13').replace('versionName = "1.9.2"','versionName = "1.9.3"')
assert 'applicationIdSuffix = ".preview"' in g
g=g.replace('applicationIdSuffix = ".preview"', 'applicationIdSuffix = ".expressive"', 1)
g+='\n\ndependencies { implementation("com.google.android.material:material:1.14.0") }\n'
p.write_text(g,encoding='utf-8')
values=root/'app/src/main/res/values';values.mkdir(parents=True,exist_ok=True)
(values/'pitchstudio_expressive_theme.xml').write_text('''<resources>
 <style name="Theme.PitchStudioExpressive" parent="Theme.Material3Expressive.Dark.NoActionBar">
  <item name="colorPrimary">#2BDBE6</item>
  <item name="colorOnPrimary">#07131C</item>
  <item name="colorSecondary">#A7B9F4</item>
  <item name="colorTertiary">#A58EFF</item>
  <item name="colorSurface">#121C2F</item>
  <item name="colorOnSurface">#F3F6FF</item>
 </style>
</resources>''',encoding='utf-8')
p=root/'app/src/main/AndroidManifest.xml';m=p.read_text(encoding='utf-8')
if re.search(r'<application[^>]*android:theme="[^"]+"',m):
    m=re.sub(r'(<application[^>]*android:theme=")[^"]+("[^>]*>)',r'\1@style/Theme.PitchStudioExpressive\2',m,count=1)
else: m=m.replace('<application ','<application android:theme="@style/Theme.PitchStudioExpressive" ',1)
p.write_text(m,encoding='utf-8')
print('Material3Expressive v1.9.3: layout reorganizado em 7 painéis; controles existentes preservados.')
