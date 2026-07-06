package com.santripesisir.roudhotuttholibin.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.santripesisir.roudhotuttholibin.R;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager.ChatMessage;

import io.noties.markwon.Markwon;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.LinkResolver;
import com.santripesisir.roudhotuttholibin.search.SearchIndexManager;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatMessage> messages;
    private final Markwon markwon;
    private final SearchIndexManager dbManager;

    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.messages = messages;
        this.dbManager = new SearchIndexManager(context);
        this.markwon = Markwon.builder(context)
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.linkResolver(new LinkResolver() {
                            @Override
                            public void resolve(@NonNull View view, @NonNull String link) {
                                if (link.startsWith("book://")) {
                                    handleBookLink(view.getContext(), link);
                                } else {
                                    try {
                                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link));
                                        view.getContext().startActivity(intent);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                    }
                })
                .build();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_bubble, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        if ("user".equalsIgnoreCase(message.role)) {
            holder.layoutUser.setVisibility(View.VISIBLE);
            holder.layoutAssistant.setVisibility(View.GONE);
            holder.tvUserMessage.setText(message.content);
        } else {
            holder.layoutUser.setVisibility(View.GONE);
            holder.layoutAssistant.setVisibility(View.VISIBLE);
            
            // Gunakan Markwon untuk me-render markdown (kutipan kitab arab, bold, dsb) secara rapi
            markwon.setMarkdown(holder.tvAssistantMessage, message.content);

            // Click listener untuk copy response
            holder.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AI Response", message.content);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(v.getContext(), "Jawaban disalin ke clipboard", Toast.LENGTH_SHORT).show();
                }
            });

            // Click listener untuk share response
            holder.btnShare.setOnClickListener(v -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message.content);
                v.getContext().startActivity(Intent.createChooser(shareIntent, "Bagikan Jawaban Fiqih"));
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutUser, layoutAssistant;
        TextView tvUserMessage, tvAssistantMessage;
        MaterialButton btnCopy, btnShare;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUser = itemView.findViewById(R.id.layout_user_chat);
            layoutAssistant = itemView.findViewById(R.id.layout_assistant_chat);
            tvUserMessage = itemView.findViewById(R.id.tv_user_message);
            tvAssistantMessage = itemView.findViewById(R.id.tv_assistant_message);
            btnCopy = itemView.findViewById(R.id.btn_copy_response);
            btnShare = itemView.findViewById(R.id.btn_share_response);
        }
    }

    private void handleBookLink(Context context, String link) {
        try {
            String cleanLink = link.substring("book://".length());
            String bookId = "";
            int pageId = 1;
            java.util.ArrayList<String> hlKeywords = new java.util.ArrayList<>();

            if (cleanLink.contains("?")) {
                String[] linkAndQuery = cleanLink.split("\\?");
                String path = linkAndQuery[0];
                String query = linkAndQuery[1];
                
                String[] pathParts = path.split("/");
                if (pathParts.length >= 2) {
                    bookId = pathParts[0];
                    pageId = Integer.parseInt(pathParts[1]);
                }

                // Parse query parameters
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("hl=")) {
                        String hlVal = param.substring("hl=".length());
                        String decoded = java.net.URLDecoder.decode(hlVal, "UTF-8");
                        for (String kw : decoded.split("\\s+|\\+")) {
                            String trimmed = kw.trim();
                            if (!trimmed.isEmpty()) {
                                hlKeywords.add(trimmed);
                            }
                        }
                    }
                }
            } else {
                String[] pathParts = cleanLink.split("/");
                if (pathParts.length >= 2) {
                    bookId = pathParts[0];
                    pageId = Integer.parseInt(pathParts[1]);
                }
            }
            int pageIndex = pageId - 1; // 0-indexed for reader

            // Query bookTitle from DB
            android.database.sqlite.SQLiteDatabase sqldb = dbManager.getReadableDatabase();
            String bookTitle = bookId;
            android.database.Cursor cursor = sqldb.rawQuery("SELECT title FROM books WHERE id = ?", new String[]{bookId});
            if (cursor.moveToFirst()) {
                bookTitle = cursor.getString(0);
            }
            cursor.close();

            // Query full page content
            String pageContent = dbManager.getPageContent(bookId, pageId);

            // Terapkan highlight di dialog menggunakan SpannableString
            android.text.SpannableString spannable = new android.text.SpannableString(pageContent);
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true);
            int colorHighlight = typedValue.data;
            if (colorHighlight == 0) {
                colorHighlight = 0xFFFFD54F; // Default yellow/amber highlight
            }

            for (String kw : hlKeywords) {
                String cleanContent = SearchIndexManager.normalizeArabic(pageContent);
                String cleanKw = SearchIndexManager.normalizeArabic(kw);

                int index = cleanContent.toLowerCase().indexOf(cleanKw.toLowerCase());
                while (index != -1) {
                    int start = 0;
                    int cleanCount = 0;
                    while (start < pageContent.length() && cleanCount < index) {
                        char c = pageContent.charAt(start);
                        if (c >= 0x064B && c <= 0x065F || c == 0x0670) {
                            start++;
                        } else {
                            start++;
                            cleanCount++;
                        }
                    }

                    int end = start;
                    int kwCleanCount = 0;
                    while (end < pageContent.length() && kwCleanCount < cleanKw.length()) {
                        char c = pageContent.charAt(end);
                        if (c >= 0x064B && c <= 0x065F || c == 0x0670) {
                            end++;
                        } else {
                            end++;
                            kwCleanCount++;
                        }
                    }

                    if (start < end && end <= pageContent.length()) {
                        spannable.setSpan(
                                new android.text.style.BackgroundColorSpan(colorHighlight),
                                start,
                                end,
                                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }

                    index = cleanContent.toLowerCase().indexOf(cleanKw.toLowerCase(), index + 1);
                }
            }

            // Tampilkan pilihan menu dialog lengkap dengan isi teks rujukan
            String finalBookTitle = bookTitle;
            final String finalBookId = bookId;
            final int finalPageId = pageId;
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(bookTitle + " (Halaman " + finalPageId + ")")
                    .setMessage(spannable) // Gunakan SpannableString untuk rendering highlight
                    .setPositiveButton("Buka Halaman Lengkap", (dialog, which) -> {
                        openBookAtPage(context, finalBookId, finalBookTitle, pageIndex, hlKeywords);
                    })
                    .setNeutralButton("Salin Teks", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Referensi Kitab",
                                "Kitab: " + finalBookTitle + "\nHalaman: " + finalPageId + "\n\n" + pageContent);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(context, "Teks referensi disalin", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Tutup", null)
                    .show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Gagal memproses link referensi", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBookAtPage(Context context, String bookId, String bookTitle, int pageIndex, java.util.ArrayList<String> hlKeywords) {
        try {
            String[] assetsFiles = context.getAssets().list("books");
            if (assetsFiles != null) {
                for (String file : assetsFiles) {
                    if (file.replace(".mai", "").equals(bookId)) {
                        Intent intent = new Intent(context, com.santripesisir.roudhotuttholibin.ui.ReaderActivity.class);
                        intent.putExtra("FILE_NAME", file);
                        intent.putExtra("BOOK_ID", bookId);
                        intent.putExtra("BOOK_TITLE", bookTitle);
                        intent.putExtra("TARGET_PAGE", pageIndex);
                        intent.putStringArrayListExtra("HIGHLIGHT_KEYWORDS", hlKeywords); // Meneruskan keywords untuk di-highlight di ReaderActivity
                        context.startActivity(intent);
                        return;
                    }
                }
            }
            Toast.makeText(context, "Berkas kitab tidak ditemukan di assets", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Gagal membuka kitab", Toast.LENGTH_SHORT).show();
        }
    }
}
